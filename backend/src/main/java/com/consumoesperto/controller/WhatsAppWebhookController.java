package com.consumoesperto.controller;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.service.TwilioWebhookAsyncProcessor;
import com.consumoesperto.service.WhatsAppBotAllowlist;
import com.consumoesperto.service.WhatsAppUserMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final TwilioWebhookAsyncProcessor twilioWebhookAsyncProcessor;
    private final WhatsAppUserMappingService whatsAppUserMappingService;
    private final WhatsAppBotAllowlist whatsAppBotAllowlist;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> receiveWebhook(@RequestParam MultiValueMap<String, String> formData) {
        String from = first(formData, "From");
        String body = first(formData, "Body");
        String mediaUrl = first(formData, "MediaUrl0");
        String mediaContentType = first(formData, "MediaContentType0");

        if (from != null && from.contains("g.us")) {
            log.info("[WhatsAppFilter] Mensagem ignorada: possível grupo {}", from);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "group-or-broadcast"));
        }

        log.debug("Webhook WhatsApp recebido de {}. Possui mídia: {}", from, mediaUrl != null && !mediaUrl.isBlank());

        Optional<Usuario> ou = whatsAppUserMappingService.findByIncomingNumber(from);
        if (ou.isEmpty()) {
            log.warn("[WhatsAppFilter] Mensagem ignorada: número não vinculado ({})", from);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "unknown-number"));
        }
        Long userId = ou.get().getId();
        if (!whatsAppBotAllowlist.isEvolutionWebhookSenderAllowed(from, userId)) {
            log.warn("[WhatsAppFilter] Mensagem Twilio ignorada: remetente não autorizado (userId={}, from={})", userId, from);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "sender-not-authorized"));
        }

        twilioWebhookAsyncProcessor.processIncomingTwilioAsync(from, body, mediaUrl, mediaContentType);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "whatsapp-webhook"));
    }

    private String first(MultiValueMap<String, String> formData, String key) {
        return formData.containsKey(key) ? formData.getFirst(key) : null;
    }
}
