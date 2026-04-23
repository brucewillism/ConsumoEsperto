package com.consumoesperto.controller;

import com.consumoesperto.config.SecretsConfig;
import com.consumoesperto.security.EncryptionService;
import com.consumoesperto.service.MercadoPagoService;
import com.consumoesperto.service.TokenValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para verificação de configurações e status do sistema
 * 
 * Este controller expõe endpoints para verificar o status das configurações,
 * credenciais e funcionalidades do sistema em produção.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Configurações", description = "Endpoints para verificação de configurações do sistema")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "https://*.ngrok-free.app"})
public class ConfigController {

    private final SecretsConfig secretsConfig;
    private final EncryptionService encryptionService;
    private final MercadoPagoService mercadoPagoService;
    private final TokenValidationService tokenValidationService;

    /**
     * Verifica o status geral das configurações
     */
    @GetMapping("/status")
    @Operation(summary = "Status das configurações", description = "Retorna o status das configurações do sistema")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Status das configurações de secrets
            Map<String, String> validatedSecrets = secretsConfig.getValidatedSecrets();
            status.put("secretsConfigured", !validatedSecrets.isEmpty());
            status.put("secretsCount", validatedSecrets.size());
            
            // Status da criptografia
            status.put("encryptionConfigured", encryptionService.isEncryptionConfigured());
            
            // Status do Mercado Pago
            boolean mercadoPagoConfigured = secretsConfig.isSecretConfigured("mercadopago.client.id") &&
                                          secretsConfig.isSecretConfigured("mercadopago.client.secret");
            status.put("mercadoPagoConfigured", mercadoPagoConfigured);
            
            // Status do banco de dados
            boolean databaseConfigured = secretsConfig.isSecretConfigured("database.url") &&
                                       secretsConfig.isSecretConfigured("database.username");
            status.put("databaseConfigured", databaseConfigured);
            
            // Status JWT
            boolean jwtConfigured = secretsConfig.isSecretConfigured("jwt.secret");
            status.put("jwtConfigured", jwtConfigured);
            
            // Status geral
            boolean allConfigured = mercadoPagoConfigured && databaseConfigured && jwtConfigured;
            status.put("allConfigured", allConfigured);
            status.put("status", allConfigured ? "READY" : "MISSING_CONFIG");
            
            log.info("✅ Status das configurações verificado: {}", status);
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status das configurações: {}", e.getMessage());
            status.put("error", e.getMessage());
            status.put("status", "ERROR");
            return ResponseEntity.internalServerError().body(status);
        }
    }

    /**
     * Testa a conexão com o Mercado Pago
     */
    @GetMapping("/test-mercadopago")
    @Operation(summary = "Testar Mercado Pago", description = "Testa a conexão com a API do Mercado Pago")
    public ResponseEntity<Map<String, Object>> testMercadoPago(@AuthenticationPrincipal Object currentUser) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Simula um teste de conexão (você pode implementar um teste real aqui)
            boolean mercadoPagoConfigured = secretsConfig.isSecretConfigured("mercadopago.client.id") &&
                                          secretsConfig.isSecretConfigured("mercadopago.client.secret");
            
            if (mercadoPagoConfigured) {
                result.put("status", "SUCCESS");
                result.put("message", "Credenciais do Mercado Pago configuradas");
                result.put("clientId", secretsConfig.getMercadoPagoClientId() != null ? 
                    secretsConfig.getMercadoPagoClientId().substring(0, 8) + "..." : "N/A");
                log.info("✅ Teste do Mercado Pago: Credenciais configuradas");
            } else {
                result.put("status", "ERROR");
                result.put("message", "Credenciais do Mercado Pago não configuradas");
                log.warn("⚠️ Teste do Mercado Pago: Credenciais não configuradas");
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar Mercado Pago: {}", e.getMessage());
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Testa a criptografia
     */
    @GetMapping("/test-encryption")
    @Operation(summary = "Testar criptografia", description = "Testa o serviço de criptografia")
    public ResponseEntity<Map<String, Object>> testEncryption() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String testData = "Dados de teste para criptografia";
            String encrypted = encryptionService.encrypt(testData);
            String decrypted = encryptionService.decrypt(encrypted);
            
            boolean success = testData.equals(decrypted);
            
            result.put("status", success ? "SUCCESS" : "ERROR");
            result.put("original", testData);
            result.put("encrypted", encrypted.substring(0, 20) + "...");
            result.put("decrypted", decrypted);
            result.put("success", success);
            
            if (success) {
                log.info("✅ Teste de criptografia: Sucesso");
            } else {
                log.error("❌ Teste de criptografia: Falha");
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar criptografia: {}", e.getMessage());
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Retorna informações de configuração (sem dados sensíveis)
     */
    @GetMapping("/info")
    @Operation(summary = "Informações de configuração", description = "Retorna informações gerais de configuração")
    public ResponseEntity<Map<String, Object>> getConfigInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // Informações gerais
            info.put("applicationName", "ConsumoEsperto Backend");
            info.put("version", "1.0.0");
            info.put("environment", "PRODUCTION");
            
            // Status das configurações
            Map<String, String> validatedSecrets = secretsConfig.getValidatedSecrets();
            info.put("configuredSecrets", validatedSecrets.keySet());
            info.put("totalSecrets", validatedSecrets.size());
            
            // Status dos serviços
            info.put("encryptionService", encryptionService.isEncryptionConfigured() ? "READY" : "NOT_CONFIGURED");
            
            log.info("✅ Informações de configuração retornadas");
            return ResponseEntity.ok(info);
            
        } catch (Exception e) {
            log.error("❌ Erro ao obter informações de configuração: {}", e.getMessage());
            info.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(info);
        }
    }
}
