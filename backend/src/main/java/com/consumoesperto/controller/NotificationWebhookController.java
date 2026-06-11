package com.consumoesperto.controller;

import com.consumoesperto.dto.NotificacaoCelularWebhookRequest;
import com.consumoesperto.dto.NotificacaoCelularWebhookResponse;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.NotificacaoCelularWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Receptores externos (MacroDroid, automações)")
@CrossOrigin(origins = {"http://localhost:14200", "https://consumoesperto.brucew07.com.br", "https://consumoesperto.brucew07.com.br:8443"})
public class NotificationWebhookController {

    @Value("${consumoesperto.webhook.notificacoes-celular.token:}")
    private String webhookToken;

    private final NotificacaoCelularWebhookService notificacaoCelularWebhookService;

    /**
     * MacroDroid: envie {@code usuarioId} no JSON + header {@code X-Webhook-Token}.
     * Com JWT no Authorization, {@code usuarioId} é opcional (inferido do token).
     */
    @PostMapping("/notificacoes-celular")
    @Operation(summary = "Receber notificação bancária do celular (MacroDroid)")
    public ResponseEntity<NotificacaoCelularWebhookResponse> notificacoesCelular(
        @RequestBody @Valid NotificacaoCelularWebhookRequest request,
        @RequestHeader(value = "X-Webhook-Token", required = false) String webhookTokenHeader,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        Long usuarioId = user != null ? user.getId() : request.getUsuarioId();
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "usuarioId no body ou autenticação JWT obrigatórios.");
        }
        if (user == null) {
            validarToken(webhookTokenHeader);
        }
        return ResponseEntity.ok(notificacaoCelularWebhookService.processar(usuarioId, request));
    }

    private void validarToken(String token) {
        if (webhookToken == null || webhookToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Webhook desativado — configure consumoesperto.webhook.notificacoes-celular.token.");
        }
        if (token == null || !webhookToken.equals(token.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Webhook-Token inválido.");
        }
    }
}
