package com.consumoesperto.controller;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller para renovação de tokens
 */
@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
public class RenovarTokenController {

    private final AutorizacaoBancariaRepository autorizacaoRepository;
    private final BankApiConfigRepository configRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Renova token do Mercado Pago
     */
    @PostMapping("/renovar-mercadopago")
    public ResponseEntity<Map<String, Object>> renovarTokenMercadoPago() {
        try {
            log.info("🔄 Iniciando renovação de token do Mercado Pago");
            
            // Buscar configuração
            Optional<BankApiConfig> configOpt = configRepository.findByTipoBanco("MERCADO_PAGO");
            if (configOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Configuração do Mercado Pago não encontrada"
                ));
            }
            
            BankApiConfig config = configOpt.get();
            
            // Buscar autorização atual
            List<AutorizacaoBancaria> authList = autorizacaoRepository.findByTipoBanco("MERCADO_PAGO");
            Optional<AutorizacaoBancaria> authOpt = authList.isEmpty() ? Optional.empty() : Optional.of(authList.get(0));
            if (authOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Autorização do Mercado Pago não encontrada"
                ));
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            
            // Tentar renovar com refresh token primeiro
            if (auth.getRefreshToken() != null && !auth.getRefreshToken().isEmpty()) {
                log.info("🔄 Tentando renovar com refresh token...");
                if (renovarComRefreshToken(config, auth)) {
                    return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Token renovado com refresh token",
                        "access_token", auth.getAccessToken()
                    ));
                }
            }
            
            // Se não conseguiu renovar, gerar novo token
            log.info("🔄 Gerando novo token...");
            if (gerarNovoToken(config, auth)) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Novo token gerado",
                    "access_token", auth.getAccessToken()
                ));
            }
            
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Não foi possível renovar o token"
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Erro interno: " + e.getMessage()
            ));
        }
    }
    
    private boolean renovarComRefreshToken(BankApiConfig config, AutorizacaoBancaria auth) {
        try {
            String url = "https://api.mercadopago.com/oauth/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            String body = String.format(
                "grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                config.getClientId(),
                config.getClientSecret(),
                auth.getRefreshToken()
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                
                auth.setAccessToken((String) tokenData.get("access_token"));
                if (tokenData.containsKey("refresh_token")) {
                    auth.setRefreshToken((String) tokenData.get("refresh_token"));
                }
                auth.setDataExpiracao(LocalDateTime.now().plusHours(6));
                auth.setDataAtualizacao(LocalDateTime.now());
                
                autorizacaoRepository.save(auth);
                log.info("✅ Token renovado com refresh token");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("⚠️ Erro ao renovar com refresh token: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean gerarNovoToken(BankApiConfig config, AutorizacaoBancaria auth) {
        try {
            String url = "https://api.mercadopago.com/oauth/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            String body = String.format(
                "grant_type=client_credentials&client_id=%s&client_secret=%s",
                config.getClientId(),
                config.getClientSecret()
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                
                auth.setAccessToken((String) tokenData.get("access_token"));
                auth.setDataExpiracao(LocalDateTime.now().plusHours(6));
                auth.setDataAtualizacao(LocalDateTime.now());
                
                autorizacaoRepository.save(auth);
                log.info("✅ Novo token gerado");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erro ao gerar novo token: {}", e.getMessage(), e);
            return false;
        }
    }
}
