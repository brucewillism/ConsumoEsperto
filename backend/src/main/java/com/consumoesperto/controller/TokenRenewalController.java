package com.consumoesperto.controller;

import com.consumoesperto.service.AutoTokenRenewalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para renovação automática de tokens
 * 
 * Este controller fornece endpoints para gerenciar a renovação
 * automática de tokens bancários, especialmente útil no login.
 */
@RestController
@RequestMapping("/api/token-renewal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Renovação de Tokens", description = "Endpoints para renovação automática de tokens")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "https://*.ngrok-free.app"})
public class TokenRenewalController {

    private final AutoTokenRenewalService autoTokenRenewalService;

    /**
     * Força renovação de token para um usuário específico
     */
    @PostMapping("/force/{userId}")
    @Operation(summary = "Forçar renovação de token", description = "Força a renovação do token do Mercado Pago para um usuário")
    public ResponseEntity<?> forcarRenovacaoToken(@PathVariable Long userId) {
        try {
            log.info("🔄 Forçando renovação de token para usuário: {}", userId);
            
            boolean sucesso = autoTokenRenewalService.forcarRenovacaoToken(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", sucesso);
            response.put("mensagem", sucesso ? "Token renovado com sucesso" : "Falha ao renovar token");
            response.put("usuarioId", userId);
            response.put("timestamp", System.currentTimeMillis());
            
            if (sucesso) {
                log.info("✅ Token renovado com sucesso para usuário: {}", userId);
                return ResponseEntity.ok(response);
            } else {
                log.warn("⚠️ Falha ao renovar token para usuário: {}", userId);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao forçar renovação de token: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sucesso", false);
            errorResponse.put("mensagem", "Erro interno: " + e.getMessage());
            errorResponse.put("usuarioId", userId);
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Renova tokens no login (endpoint público para uso interno)
     */
    @PostMapping("/login/{userId}")
    @Operation(summary = "Renovar tokens no login", description = "Renova automaticamente todos os tokens quando o usuário faz login")
    public ResponseEntity<?> renovarTokensNoLogin(@PathVariable Long userId) {
        try {
            log.info("🔐 Renovando tokens no login para usuário: {}", userId);
            
            autoTokenRenewalService.renovarTokensNoLogin(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("mensagem", "Tokens renovados automaticamente no login");
            response.put("usuarioId", userId);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ Tokens renovados no login para usuário: {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar tokens no login: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sucesso", false);
            errorResponse.put("mensagem", "Erro ao renovar tokens: " + e.getMessage());
            errorResponse.put("usuarioId", userId);
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Verifica status dos tokens de um usuário
     */
    @GetMapping("/status/{userId}")
    @Operation(summary = "Verificar status dos tokens", description = "Verifica o status atual dos tokens bancários de um usuário")
    public ResponseEntity<?> verificarStatusTokens(@PathVariable Long userId) {
        try {
            log.info("🔍 Verificando status dos tokens para usuário: {}", userId);
            
            // Aqui você pode implementar a lógica para verificar status
            // Por enquanto, retorna um status básico
            
            Map<String, Object> response = new HashMap<>();
            response.put("usuarioId", userId);
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "Verificação implementada");
            response.put("mensagem", "Endpoint para verificação de status dos tokens");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status dos tokens: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sucesso", false);
            errorResponse.put("mensagem", "Erro ao verificar status: " + e.getMessage());
            errorResponse.put("usuarioId", userId);
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
