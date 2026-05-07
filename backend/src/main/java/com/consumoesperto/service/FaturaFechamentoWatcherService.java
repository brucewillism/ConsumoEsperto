package com.consumoesperto.service;

import com.consumoesperto.dto.MelhorDiaCompraCalculado;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.NotificacaoFechamentoCartao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.NotificacaoFechamentoCartaoRepository;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Watcher: no dia estimado de fechamento do cartão, compara fatura aberta com saldo e envia WhatsApp (uma vez por ciclo).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaturaFechamentoWatcherService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DATA_PT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final FaturaService faturaService;
    private final CartaoCreditoService cartaoCreditoService;
    private final SaldoService saldoService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final NotificacaoFechamentoCartaoRepository notificacaoFechamentoCartaoRepository;

    @Value("${consumoesperto.watcher.fechamento.enabled:true}")
    private boolean watcherEnabled;

    @Value("${consumoesperto.watcher.zone:America/Sao_Paulo}")
    private String watcherZone;

    @Scheduled(cron = "${consumoesperto.watcher.fechamento.cron:0 0 9 * * *}", zone = "${consumoesperto.watcher.zone:America/Sao_Paulo}")
    @Transactional
    public void executarDiario() {
        if (!watcherEnabled) {
            return;
        }
        ZoneId zone = ZoneId.of(watcherZone != null && !watcherZone.isBlank() ? watcherZone : "America/Sao_Paulo");
        LocalDate hoje = LocalDate.now(zone);
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findAllAtivosComUsuario();
        log.info("[WATCHER-FECHAMENTO] Execução diária cartões ativos={} data={}", cartoes.size(), hoje);
        for (CartaoCredito c : cartoes) {
            try {
                processarCartaoSeFechamentoHoje(c, hoje);
            } catch (Exception e) {
                log.warn("[WATCHER-FECHAMENTO] cartaoId={} erro: {}", c.getId(), e.getMessage());
            }
        }
    }

    void processarCartaoSeFechamentoHoje(CartaoCredito cartao, LocalDate hoje) {
        Usuario u = cartao.getUsuario();
        if (u == null || u.getId() == null) {
            return;
        }
        Long userId = u.getId();
        MelhorDiaCompraCalculado s = faturaService.calcularMelhorDiaCompra(cartao.getId(), userId);
        LocalDate fechamento = s.dataFechamentoEstimada();
        if (fechamento == null || !fechamento.equals(hoje)) {
            return;
        }
        if (notificacaoFechamentoCartaoRepository.existsByCartaoCreditoIdAndDataFechamentoReferencia(cartao.getId(), hoje)) {
            log.debug("[WATCHER-FECHAMENTO] Já notificado cartaoId={} data={}", cartao.getId(), hoje);
            return;
        }
        BigDecimal valorFatura = cartaoCreditoService.calcularLimiteUtilizadoAberto(cartao.getId());
        if (valorFatura == null) {
            valorFatura = BigDecimal.ZERO;
        }
        BigDecimal saldo = saldoService.saldoContaCorrente(userId);
        if (saldo == null) {
            saldo = BigDecimal.ZERO;
        }
        String destino = resolverTelefoneWhatsApp(userId, u);
        if (destino == null || destino.isBlank()) {
            log.info("[WATCHER-FECHAMENTO] Sem telefone WhatsApp userId={} cartaoId={}", userId, cartao.getId());
            return;
        }
        String nomeCartao = cartao.getNome() != null ? cartao.getNome() : "Cartão";
        String vencStr = s.proximoVencimentoCiclo() != null ? s.proximoVencimentoCiclo().format(DATA_PT) : "—";
        String vocativo = jarvisProtocolService.resolveVocative(userId, usuarioRepository);
        String valorFat = BRL.format(valorFatura);
        String saldoFmt = BRL.format(saldo);
        String msg;
        String tipo;
        if (saldo.compareTo(valorFatura) >= 0) {
            tipo = "OK";
            msg = jarvisProtocolService.proativoFaturaFechamentoSaldoCobre(vocativo, nomeCartao, valorFat, saldoFmt);
        } else {
            tipo = "DEFICIT";
            BigDecimal falta = valorFatura.subtract(saldo).max(BigDecimal.ZERO);
            msg = jarvisProtocolService.proativoFaturaFechamentoDeficitFluxo(
                vocativo, nomeCartao, valorFat, saldoFmt, BRL.format(falta), vencStr);
        }
        boolean enviado = whatsAppNotificationService.enviarParaUsuario(userId, msg);
        if (!enviado) {
            log.warn("[WATCHER-FECHAMENTO] WhatsApp não enviado userId={} cartaoId={} [J.A.R.V.I.S. Offline]", userId, cartao.getId());
            return;
        }
        NotificacaoFechamentoCartao n = new NotificacaoFechamentoCartao();
        n.setCartaoCredito(cartao);
        n.setDataFechamentoReferencia(hoje);
        n.setTipo(tipo);
        n.setMensagemPreview(msg.length() > 400 ? msg.substring(0, 397) + "…" : msg);
        notificacaoFechamentoCartaoRepository.save(n);
        log.info("[WATCHER-FECHAMENTO] Enviado tipo={} cartaoId={} userId={}", tipo, cartao.getId(), userId);
    }

    private String resolverTelefoneWhatsApp(Long userId, Usuario u) {
        if (u.getWhatsappNumero() != null && !u.getWhatsappNumero().isBlank()) {
            return u.getWhatsappNumero().trim();
        }
        return usuarioAiConfigRepository.findByUsuarioId(userId)
            .map(cfg -> cfg.getWhatsappOwnerPhone())
            .filter(p -> p != null && !p.isBlank())
            .map(String::trim)
            .orElse(null);
    }
}
