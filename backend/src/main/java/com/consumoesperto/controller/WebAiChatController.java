package com.consumoesperto.controller;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.CognitiveGatewaySelector;
import com.consumoesperto.edith.CognitiveRequest;
import com.consumoesperto.edith.CognitiveResponse;
import com.consumoesperto.edith.EdithIntegrationService;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AiRateLimitService;
import com.consumoesperto.service.JarvisProtocolService;
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

    private final CognitiveGatewaySelector cognitiveGatewaySelector;
    private final EdithProperties edithProperties;
    private final EdithIntegrationService edithIntegrationService;
    private final JarvisProtocolService jarvisProtocolService;
    private final AiRateLimitService aiRateLimitService;

    @PostMapping
    public ResponseEntity<Map<String, String>> perguntar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, String> body
    ) {
        aiRateLimitService.checkOrThrow(user.getId(), "ia-chat-web");
        String mensagem = body.getOrDefault("mensagem", "");

        if (edithProperties.isEnabled()) {
            if (!edithIntegrationService.isOperational()) {
                return ResponseEntity.status(503).body(Map.of(
                    "resposta", "",
                    "error", "EDITH_UNAVAILABLE"
                ));
            }
            CognitiveResponse response = cognitiveGatewaySelector.active().send(CognitiveRequest.builder()
                .usuarioId(user.getId())
                .content(mensagem)
                .sourceAction("consumo.chat")
                .awaitCompletion(true)
                .build());
            String texto = response.getResultText() != null ? response.getResultText() : "";
            String assinada = jarvisProtocolService.assinaturaCondicional(user.getId(), texto);
            return ResponseEntity.ok(Map.of("resposta", assinada != null ? assinada : ""));
        }

        CognitiveResponse legacy = cognitiveGatewaySelector.active().send(CognitiveRequest.builder()
            .usuarioId(user.getId())
            .content(mensagem)
            .sourceAction("consumo.chat")
            .awaitCompletion(true)
            .build());
        String assinada = legacy.getResultText() != null
            ? jarvisProtocolService.assinaturaCondicional(user.getId(), legacy.getResultText())
            : "";
        return ResponseEntity.ok(Map.of("resposta", assinada != null ? assinada : ""));
    }
}
