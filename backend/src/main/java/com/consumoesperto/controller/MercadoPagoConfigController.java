package com.consumoesperto.controller;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AutorizacaoBancariaService;
import com.consumoesperto.service.BankApiConfigService;
import com.consumoesperto.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mercadopago/config")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoConfigController {

    private final AutorizacaoBancariaService autorizacaoBancariaService;
    private final BankApiConfigService bankApiConfigService;
    private final UsuarioService usuarioService;

    /**
     * Configura credenciais reais do Mercado Pago
     */
    @PostMapping("/credentials")
    public ResponseEntity<Map<String, Object>> configureCredentials(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, String> credentials) {
        
        try {
            log.info("🔧 Configurando credenciais reais do Mercado Pago para usuário: {}", currentUser.getId());
            
            String clientId = credentials.get("clientId");
            String clientSecret = credentials.get("clientSecret");
            String accessToken = credentials.get("accessToken");
            String refreshToken = credentials.get("refreshToken");
            
            if (clientId == null || clientSecret == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Client ID e Client Secret são obrigatórios",
                    "success", false
                ));
            }

            // 1. Atualizar configuração da API
            Optional<BankApiConfig> configOpt = bankApiConfigService.findByUsuarioIdAndBanco(
                currentUser.getId(), "MERCADOPAGO");
            
            if (configOpt.isPresent()) {
                BankApiConfig config = configOpt.get();
                config.setClientId(clientId);
                config.setClientSecret(clientSecret);
                config.setDataAtualizacao(LocalDateTime.now());
                bankApiConfigService.saveConfig(config);
                log.info("✅ Configuração da API atualizada");
            } else {
                // Criar nova configuração
                BankApiConfig newConfig = new BankApiConfig();
                newConfig.setTipoBanco("MERCADOPAGO");
                newConfig.setBanco("MERCADOPAGO"); // Campo obrigatório
                newConfig.setNome("Mercado Pago");
                newConfig.setClientId(clientId);
                newConfig.setClientSecret(clientSecret);
                newConfig.setApiUrl("https://api.mercadopago.com/v1");
                newConfig.setRedirectUri("https://0d723f1e294f.ngrok-free.app/api/bank/oauth/mercadopago/callback");
                newConfig.setScope("read write");
                newConfig.setAtivo(true);
                newConfig.setDataCriacao(LocalDateTime.now());
                newConfig.setDataAtualizacao(LocalDateTime.now());
                
                // Buscar o usuário e associar à configuração
                try {
                    com.consumoesperto.model.Usuario usuario = usuarioService.findById(currentUser.getId());
                    newConfig.setUsuario(usuario);
                } catch (Exception e) {
                    log.error("❌ Erro ao buscar usuário: {}", e.getMessage());
                    return ResponseEntity.badRequest().body(Map.of(
                        "erro", "Usuário não encontrado",
                        "success", false
                    ));
                }
                
                bankApiConfigService.saveConfig(newConfig);
                log.info("✅ Nova configuração da API criada");
            }

            // 2. Configurar autorização bancária (se tokens fornecidos)
            if (accessToken != null) {
                Map<String, Object> tokenResponse = new HashMap<>();
                tokenResponse.put("access_token", accessToken);
                if (refreshToken != null) {
                    tokenResponse.put("refresh_token", refreshToken);
                }
                tokenResponse.put("token_type", "Bearer");
                tokenResponse.put("scope", "read write");
                tokenResponse.put("expires_in", 21600); // 6 horas
                
                try {
                    autorizacaoBancariaService.salvarAutorizacao(
                        currentUser.getId(), 
                        com.consumoesperto.service.BankApiService.BankType.MERCADO_PAGO, 
                        tokenResponse
                    );
                    log.info("✅ Autorização bancária configurada");
                } catch (Exception e) {
                    log.warn("⚠️ Erro ao configurar autorização bancária: {}", e.getMessage());
                }
            }

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", true);
            resultado.put("message", "Credenciais do Mercado Pago configuradas com sucesso!");
            resultado.put("clientId", clientId);
            resultado.put("hasAccessToken", accessToken != null);
            resultado.put("timestamp", LocalDateTime.now());

            log.info("🎉 Configuração do Mercado Pago concluída para usuário: {}", currentUser.getId());

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na configuração: " + e.getMessage(),
                "success", false
            ));
        }
    }

    /**
     * Obtém status da configuração atual
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConfigStatus(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Verificar configuração da API
            Optional<BankApiConfig> configOpt = bankApiConfigService.findByUsuarioIdAndBanco(
                currentUser.getId(), "MERCADOPAGO");
            
            boolean hasApiConfig = configOpt.isPresent();
            status.put("hasApiConfig", hasApiConfig);
            
            if (hasApiConfig) {
                BankApiConfig config = configOpt.get();
                status.put("clientId", config.getClientId());
                status.put("hasClientSecret", config.getClientSecret() != null && 
                    !config.getClientSecret().equals("CONFIGURAR_MERCADOPAGO_CLIENT_SECRET"));
                status.put("apiUrl", config.getApiUrl());
                status.put("isActive", config.getAtivo());
            }
            
            // Verificar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaService
                .buscarAutorizacao(currentUser.getId(), com.consumoesperto.service.BankApiService.BankType.MERCADO_PAGO);
            
            boolean hasAuth = authOpt.isPresent();
            status.put("hasAuth", hasAuth);
            
            if (hasAuth) {
                AutorizacaoBancaria auth = authOpt.get();
                status.put("hasAccessToken", auth.getAccessToken() != null);
                status.put("hasRefreshToken", auth.getRefreshToken() != null);
                status.put("isTokenExpired", auth.getDataExpiracao().isBefore(LocalDateTime.now()));
                status.put("isActive", auth.getAtivo());
                
                // Se tem autorização bancária, considerar como configurado mesmo sem BankApiConfig
                if (auth.getAccessToken() != null && auth.getAtivo()) {
                    status.put("isConfigured", true);
                }
            } else {
                status.put("isConfigured", false);
            }
            
            status.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Erro ao obter status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("erro", e.getMessage()));
        }
    }
}
