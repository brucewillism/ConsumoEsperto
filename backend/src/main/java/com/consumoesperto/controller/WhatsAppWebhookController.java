package com.consumoesperto.controller;

import com.consumoesperto.service.WhatsAppCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppCommandService whatsAppCommandService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> receiveWebhook(@RequestParam MultiValueMap<String, String> formData) {
        String from = first(formData, "From");
        String body = first(formData, "Body");
        String mediaUrl = first(formData, "MediaUrl0");
        String mediaContentType = first(formData, "MediaContentType0");

        log.debug("Webhook WhatsApp recebido de {}. Possui mídia: {}", from, mediaUrl != null && !mediaUrl.isBlank());
        whatsAppCommandService.processIncomingMessage(from, body, mediaUrl, mediaContentType);

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
