package com.consumoesperto.service;

import com.consumoesperto.dto.EvolutionIncomingMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookAsyncProcessor {

    private final WhatsAppCommandService whatsAppCommandService;
    private final EvolutionInstanceSettingsService evolutionInstanceSettingsService;

    /** Processamento pesado (IA/OCR) no cerebroExecutor — evita bloquear o pool curto do webhook. */
    @Async("cerebroExecutor")
    public void processEvolutionMessageAsync(EvolutionIncomingMessageDTO incoming, Long userId, String evolutionInstanceName) {
        try {
            whatsAppCommandService.processIncomingEvolutionMessage(incoming, userId, evolutionInstanceName);
            if (evolutionInstanceName != null && !evolutionInstanceName.isBlank()) {
                evolutionInstanceSettingsService.schedulePresenceRefreshAfterActivity(evolutionInstanceName);
            }
        } catch (Throwable t) {
            log.error("[WhatsAppFilter] Erro assíncrono ao processar Evolution userId={} remoteJid={}: {}",
                userId, incoming != null ? incoming.getFromJid() : null, t.getMessage(), t);
            if (incoming != null && userId != null) {
                whatsAppCommandService.notifyWebhookAsyncFailure(incoming, userId, evolutionInstanceName, t);
            }
        }
    }
}
