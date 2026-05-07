package com.consumoesperto.service;

import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.WhatsAppLembretePendencia;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.repository.WhatsAppLembretePendenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoService {

    private final TransacaoRepository transacaoRepository;
    private final WhatsAppLembretePendenciaRepository lembretePendenciaRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    @Scheduled(cron = "0 0 10 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void lembrarPendenciasConferencia() {
        LocalDateTime limite = LocalDateTime.now().minusHours(24);
        List<Transacao> candidatas = transacaoRepository.findPendentesParaLembrete(
            limite,
            WhatsAppLembretePendencia.TIPO_PENDENCIA_CONFERENCIA
        );
        if (candidatas.isEmpty()) {
            return;
        }

        Map<Long, List<Transacao>> porUsuario = candidatas.stream()
            .filter(t -> t.getUsuario() != null && t.getUsuario().getId() != null)
            .collect(Collectors.groupingBy(t -> t.getUsuario().getId()));

        List<WhatsAppLembretePendencia> novosRegistros = new ArrayList<>();

        for (Map.Entry<Long, List<Transacao>> e : porUsuario.entrySet()) {
            Usuario usuario = e.getValue().get(0).getUsuario();
            String whatsapp = usuario.getWhatsappNumero();
            if (whatsapp == null || whatsapp.isBlank()) {
                log.debug("Usuário {} sem WhatsApp vinculado; pulando lembrete.", e.getKey());
                continue;
            }
            int x = e.getValue().size();
            String vocativo = jarvisProtocolService.resolveVocative(usuario.getId(), usuarioRepository);
            String msg = jarvisProtocolService.proativoLembreteConferenciaNotas(vocativo, x);
            boolean enviado = whatsAppNotificationService.enviarParaUsuario(usuario.getId(), msg);
            if (!enviado) {
                log.warn("[J.A.R.V.I.S. Offline] Lembrete conferência não enviado para usuário {}.", e.getKey());
                continue;
            }
            LocalDateTime agora = LocalDateTime.now();
            for (Transacao t : e.getValue()) {
                WhatsAppLembretePendencia row = new WhatsAppLembretePendencia();
                row.setUsuarioId(usuario.getId());
                row.setTransacaoId(t.getId());
                row.setTipo(WhatsAppLembretePendencia.TIPO_PENDENCIA_CONFERENCIA);
                row.setEnviadoEm(agora);
                novosRegistros.add(row);
            }
        }

        if (!novosRegistros.isEmpty()) {
            lembretePendenciaRepository.saveAll(novosRegistros);
        }
    }
}
