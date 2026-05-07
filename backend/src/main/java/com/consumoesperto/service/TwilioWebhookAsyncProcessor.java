package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioWebhookAsyncProcessor {

    private final WhatsAppCommandService whatsAppCommandService;

    @Async("whatsappWebhookExecutor")
    public void processIncomingTwilioAsync(String from, String body, String mediaUrl, String mediaContentType) {
        try {
            whatsAppCommandService.processIncomingMessage(from, body, mediaUrl, mediaContentType);
        } catch (Exception e) {
            log.error("[WhatsAppFilter] Erro assíncrono Twilio webhook from={}: {}", from, e.getMessage(), e);
        }
    }
}
