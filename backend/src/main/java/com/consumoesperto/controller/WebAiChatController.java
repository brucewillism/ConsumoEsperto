package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AiRateLimitService;
import com.consumoesperto.service.JarvisProtocolService;
import com.consumoesperto.service.WhatsAppCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ia-chat")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class WebAiChatController {

    private final WhatsAppCommandService whatsAppCommandService;
    private final JarvisProtocolService jarvisProtocolService;
    private final AiRateLimitService aiRateLimitService;

    @PostMapping
    public ResponseEntity<Map<String, String>> perguntar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, String> body
    ) {
        aiRateLimitService.checkOrThrow(user.getId(), "ia-chat-web");
        String resposta = whatsAppCommandService.processWebCommand(user.getId(), body.getOrDefault("mensagem", ""));
        String assinada = resposta != null ? jarvisProtocolService.assinaturaCondicional(user.getId(), resposta) : "";
        return ResponseEntity.ok(Map.of("resposta", assinada != null ? assinada : ""));
    }
}
