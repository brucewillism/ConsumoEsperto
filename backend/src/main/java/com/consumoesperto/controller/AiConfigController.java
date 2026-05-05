package com.consumoesperto.controller;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.security.SecurityService;
import com.consumoesperto.service.AiProvidersConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * API HTTP para configuração de IA por usuário (PostgreSQL, {@code usuario_ai_config}).
 */
@RestController
@RequestMapping("/api/config")
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
@RequiredArgsConstructor
@Slf4j
public class AiConfigController {

    private final AiProvidersConfigService aiProvidersConfigService;
    private final SecurityService securityService;

    @GetMapping("/ia")
    public ResponseEntity<?> getIaConfig() {
        Optional<Usuario> usuarioOpt = securityService.getCurrentUser();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        return ResponseEntity.ok(aiProvidersConfigService.getConfigForApiResponse(usuarioOpt.get().getId()));
    }

    @PostMapping("/ia")
    public ResponseEntity<Map<String, Object>> salvarIaConfig(@RequestBody(required = false) AiProvidersConfigService.AiProvidersConfig payload) {
        try {
            Optional<Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Usuario nao autenticado"
                ));
            }
            if (payload == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Payload de configuracao de IA vazio ou invalido"
                ));
            }
            aiProvidersConfigService.persistConfigFromApiRequest(usuarioOpt.get().getId(), payload);
            log.info("Configuracao de IA persistida (usuario id={})", usuarioOpt.get().getId());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Configuracoes de IA salvas"
            ));
        } catch (Exception e) {
            String safeMessage = (e.getMessage() == null || e.getMessage().isBlank())
                ? "Falha ao persistir configuracao de IA"
                : e.getMessage();
            log.error("Erro ao salvar config de IA: {}", safeMessage, e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", safeMessage
            ));
        }
    }
}
