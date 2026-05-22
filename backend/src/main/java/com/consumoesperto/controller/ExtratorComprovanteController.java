package com.consumoesperto.controller;

import com.consumoesperto.dto.ExtratorComprovanteWebhookRequest;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ExtratorComprovanteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/extrator")
@RequiredArgsConstructor
@Tag(name = "Extrator de Comprovantes", description = "Webhook para MacroDroid/Tasker/e-mail PIX")
@CrossOrigin(origins = {"http://localhost:14200", "https://0d723f1e294f.ngrok-free.app"})
public class ExtratorComprovanteController {

    @Value("${consumoesperto.extrator.webhook-token:}")
    private String webhookToken;

    private final ExtratorComprovanteService extratorComprovanteService;

    /**
     * Modo público: envie {@code usuarioId} + {@code token} no body (MacroDroid/Tasker).
     * Modo autenticado: JWT no header — {@code usuarioId} inferido do token.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Interpretar notificação/e-mail de comprovante e lançar transação")
    public ResponseEntity<TransacaoDTO> webhook(
        @RequestBody @Valid ExtratorComprovanteWebhookRequest request,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        Long usuarioId = user != null ? user.getId() : request.getUsuarioId();
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "usuarioId ou autenticação JWT obrigatórios.");
        }
        if (user == null) {
            validarToken(request.getToken());
        }
        return ResponseEntity.ok(extratorComprovanteService.processarWebhook(usuarioId, request));
    }

    private void validarToken(String token) {
        if (webhookToken == null || webhookToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Webhook público desativado — configure consumoesperto.extrator.webhook-token.");
        }
        if (token == null || !webhookToken.equals(token.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de extrator inválido.");
        }
    }
}
