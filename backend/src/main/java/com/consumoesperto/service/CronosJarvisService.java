package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Camada Cronos: agenda Google + Modo Viagem (meta temporária) + fila de confirmação WhatsApp.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CronosJarvisService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final JdbcTemplate jdbcTemplate;
    private final GoogleCalendarService googleCalendarService;
    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final MetaFinanceiraService metaFinanceiraService;
    private final JarvisProtocolService jarvisProtocolService;
    private final WhatsAppNotificationService whatsAppNotificationService;

    private final Map<Long, ArrayDeque<ModoViagemPending>> filaModoViagemWhatsApp = new ConcurrentHashMap<>();

    public record ModoViagemPending(String eventIdGoogle, String titulo, LocalDate dataEvento, BigDecimal tetoSugerido) {
    }

    public void recarregarFilaWhatsApp(Long usuarioId, ModoViagemPending pending) {
        ArrayDeque<ModoViagemPending> q = filaModoViagemWhatsApp.computeIfAbsent(usuarioId, k -> new ArrayDeque<>());
        q.removeIf(p -> p.eventIdGoogle() != null && p.eventIdGoogle().equals(pending.eventIdGoogle()));
        q.add(pending);
    }

    public Optional<ModoViagemPending> pollModoViagemParaConfirmacaoWhatsApp(Long usuarioId) {
        ArrayDeque<ModoViagemPending> q = filaModoViagemWhatsApp.get(usuarioId);
        if (q == null || q.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(q.peek());
    }

    public void descartarTopoModoViagem(Long usuarioId) {
        ArrayDeque<ModoViagemPending> q = filaModoViagemWhatsApp.get(usuarioId);
        if (q != null && !q.isEmpty()) {
            q.poll();
            if (q.isEmpty()) {
                filaModoViagemWhatsApp.remove(usuarioId);
            }
        }
    }

    public boolean tentarReservarNotificacao(Long usuarioId, String fingerprint) {
        int n = jdbcTemplate.update(
            "INSERT INTO jarvis_cronos_evento_log (usuario_id, event_fingerprint, created_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (usuario_id, event_fingerprint) DO NOTHING",
            usuarioId, fingerprint
        );
        return n > 0;
    }

    /**
     * Notifica utilizadores com WhatsApp e Calendar vinculado — eventos na janela de 7 dias.
     */
    @Scheduled(cron = "0 0 10 * * MON", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void jobEscanearAgendaSemanal() {
        for (var u : usuarioRepository.findAll()) {
            if (u.getWhatsappNumero() == null || u.getWhatsappNumero().isBlank()) {
                continue;
            }
            if (u.getGoogleCalendarRefreshToken() == null || u.getGoogleCalendarRefreshToken().isBlank()) {
                continue;
            }
            try {
                processarUsuario(u.getId());
            } catch (Exception e) {
                log.warn("[CRONOS] scan user {}: {}", u.getId(), e.getMessage());
            }
        }
    }

    private void processarUsuario(Long usuarioId) {
        List<GoogleCalendarService.EventoAgendaRelevante> evs = googleCalendarService.listarEventosRelevantes(usuarioId, 14);
        for (GoogleCalendarService.EventoAgendaRelevante ev : evs) {
            String fp = (ev.idGoogle() != null && !ev.idGoogle().isBlank())
                ? ev.idGoogle()
                : ev.titulo() + "|" + ev.dataInicio();
            if (!tentarReservarNotificacao(usuarioId, fp)) {
                continue;
            }
            BigDecimal teto = sugerirTetoModoViagem(usuarioId);
            ModoViagemPending pending = new ModoViagemPending(ev.idGoogle(), ev.titulo(), ev.dataInicio(), teto);
            recarregarFilaWhatsApp(usuarioId, pending);
            String voc = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
            String msg = jarvisProtocolService.proativoModoViagemSugestao(voc, ev.titulo(), BRL.format(teto));
            whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
        }
    }

    private BigDecimal sugerirTetoModoViagem(Long usuarioId) {
        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime ini = fim.minusMonths(3);
        BigDecimal d = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, ini, fim));
        BigDecimal mediaMes = d.compareTo(BigDecimal.ZERO) <= 0
            ? new BigDecimal("2000")
            : d.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        return mediaMes.divide(BigDecimal.valueOf(4), 0, RoundingMode.HALF_UP)
            .max(new BigDecimal("500"))
            .min(new BigDecimal("15000"));
    }

    @Transactional
    public void aceitarModoViagemTopoDaFila(Long usuarioId) {
        ModoViagemPending p = pollModoViagemParaConfirmacaoWhatsApp(usuarioId)
            .orElseThrow(() -> new IllegalStateException("Nenhuma sugestão Modo Viagem pendente."));
        LocalDate expira = p.dataEvento() != null ? p.dataEvento().plusDays(14) : LocalDate.now().plusWeeks(2);
        metaFinanceiraService.criarMetaTemporariaModoViagem(
            usuarioId,
            p.titulo(),
            p.tetoSugerido(),
            expira,
            p.eventIdGoogle()
        );
        descartarTopoModoViagem(usuarioId);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
