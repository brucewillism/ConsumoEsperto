package com.consumoesperto.autonomy;

import com.consumoesperto.dto.NotificacaoSolicitacao;
import com.consumoesperto.model.NotificacaoEventoTipo;
import com.consumoesperto.service.NotificationOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Única implementação actual: WhatsApp + web via orquestrador existente.
 * Entrega Evolution/WhatsApp real: IMPLEMENTADO NÃO VALIDADO enquanto o canal estiver indisponível.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrchestratorJarvisNotificationChannel implements JarvisNotificationChannel {

    private final NotificationOrchestratorService orchestrator;

    @Override
    public String name() {
        return "ORCHESTRATOR";
    }

    @Override
    public boolean deliver(Long usuarioId, String title, String message, JarvisNotificationPriority priority) {
        return deliver(usuarioId, title, message, priority, null, null);
    }

    @Override
    public boolean deliver(
        Long usuarioId,
        String title,
        String message,
        JarvisNotificationPriority priority,
        Long financialEventId,
        Long resourceId
    ) {
        try {
            NotificacaoEventoTipo evento = mapEvento(priority);
            String hash = idempotencyHash(usuarioId, evento, financialEventId, resourceId, title);
            return orchestrator.solicitar(NotificacaoSolicitacao.builder()
                .usuarioId(usuarioId)
                .evento(evento)
                .mensagem(message)
                .digestLinha(title)
                .tituloWeb(title != null ? title : "J.A.R.V.I.S.")
                .hashEvento(hash)
                .financialEventId(financialEventId)
                .resourceId(resourceId)
                .decisionId(title)
                .build());
        } catch (Exception e) {
            log.debug("JARVIS canal indisponível userId={}: {}", usuarioId, e.getMessage());
            return false;
        }
    }

    @Override
    public void retryPending() {
        orchestrator.retryPendingActionRequired();
    }

    static NotificacaoEventoTipo mapEvento(JarvisNotificationPriority priority) {
        if (priority == null) {
            return NotificacaoEventoTipo.GENERICO;
        }
        return switch (priority) {
            case CRITICAL -> NotificacaoEventoTipo.SALDO_NEGATIVO_PREVISTO;
            case WARNING -> NotificacaoEventoTipo.ORCAMENTO_LIMITE;
            case ACTION_REQUIRED -> NotificacaoEventoTipo.ACTION_REQUIRED;
            case NOTICE, INFO -> NotificacaoEventoTipo.GENERICO;
        };
    }

    static String idempotencyHash(
        Long usuarioId,
        NotificacaoEventoTipo evento,
        Long financialEventId,
        Long resourceId,
        String decisionId
    ) {
        return usuarioId
            + "|" + (evento != null ? evento.name() : "NA")
            + "|" + (financialEventId != null ? financialEventId : "na")
            + "|" + (resourceId != null ? resourceId : "na")
            + "|" + (decisionId != null && !decisionId.isBlank() ? decisionId : "na");
    }
}
