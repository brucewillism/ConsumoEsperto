package com.consumoesperto.controller;

import com.consumoesperto.service.MercadoPagoTokenRefreshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para gerenciar renovação de tokens do Mercado Pago
 */
@RestController
@RequestMapping("/api/token-refresh")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Renovação de Token", description = "Endpoints para renovação de tokens do Mercado Pago")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "https://*.ngrok-free.app"})
public class TokenRefreshController {

    private final MercadoPagoTokenRefreshService tokenRefreshService;

    /**
     * Renova o token do Mercado Pago para um usuário
     */
    @PostMapping("/mercadopago/{userId}")
    @Operation(summary = "Renovar token do Mercado Pago", 
               description = "Renova o token de acesso do Mercado Pago usando o refresh token")
    public ResponseEntity<Map<String, Object>> renovarTokenMercadoPago(@PathVariable Long userId) {
        try {
            log.info("🔄 Solicitação de renovação de token do Mercado Pago para usuário: {}", userId);
            
            boolean sucesso = tokenRefreshService.renovarToken(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", sucesso);
            response.put("userId", userId);
            response.put("message", sucesso ? "Token renovado com sucesso" : "Falha ao renovar token");
            
            if (sucesso) {
                log.info("✅ Token renovado com sucesso para usuário: {}", userId);
                return ResponseEntity.ok(response);
            } else {
                log.warn("⚠️ Falha ao renovar token para usuário: {}", userId);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token para usuário {}: {}", userId, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("userId", userId);
            response.put("message", "Erro interno: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Verifica se o token precisa ser renovado
     */
    @GetMapping("/mercadopago/{userId}/status")
    @Operation(summary = "Verificar status do token", 
               description = "Verifica se o token do Mercado Pago precisa ser renovado")
    public ResponseEntity<Map<String, Object>> verificarStatusToken(@PathVariable Long userId) {
        try {
            log.info("🔍 Verificando status do token do Mercado Pago para usuário: {}", userId);
            
            boolean precisaRenovar = tokenRefreshService.tokenPrecisaRenovar(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("precisaRenovar", precisaRenovar);
            response.put("message", precisaRenovar ? "Token precisa ser renovado" : "Token ainda válido");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status do token para usuário {}: {}", userId, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("precisaRenovar", true);
            response.put("message", "Erro ao verificar status: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Renova o token se necessário
     */
    @PostMapping("/mercadopago/{userId}/auto")
    @Operation(summary = "Renovação automática de token", 
               description = "Renova o token apenas se necessário")
    public ResponseEntity<Map<String, Object>> renovarTokenSeNecessario(@PathVariable Long userId) {
        try {
            log.info("🔄 Solicitação de renovação automática de token para usuário: {}", userId);
            
            boolean sucesso = tokenRefreshService.renovarTokenSeNecessario(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", sucesso);
            response.put("userId", userId);
            response.put("message", sucesso ? "Token verificado/renovado com sucesso" : "Falha na verificação/renovação");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro na renovação automática para usuário {}: {}", userId, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("userId", userId);
            response.put("message", "Erro interno: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
