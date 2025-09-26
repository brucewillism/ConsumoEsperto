package com.consumoesperto.controller;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/**
 * Controller para gerar tokens de PRODUÇÃO do Mercado Pago
 */
@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
public class GerarTokenProducaoController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RestTemplate restTemplate;

    /**
     * Gera um token de PRODUÇÃO válido para o Mercado Pago
     */
    @PostMapping("/gerar-producao")
    public ResponseEntity<Map<String, Object>> gerarTokenProducao(@RequestParam Long usuarioId) {
        try {
            log.info("🚀 Gerando token de PRODUÇÃO para usuário: {}", usuarioId);

            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository
                .findByUsuarioIdAndBanco(usuarioId, "MERCADO_PAGO");

            if (configOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Configuração do Mercado Pago não encontrada",
                    "usuarioId", usuarioId
                ));
            }

            BankApiConfig config = configOpt.get();
            
            if (config.getClientSecret() == null || config.getClientSecret().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Client Secret não configurado",
                    "usuarioId", usuarioId
                ));
            }

            // Gerar token de PRODUÇÃO usando Client Credentials
            String accessToken = gerarAccessTokenProducao(config.getClientId(), config.getClientSecret());
            
            if (accessToken == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Falha ao gerar token de PRODUÇÃO",
                    "usuarioId", usuarioId
                ));
            }

            // Atualizar autorização bancária com o novo token
            atualizarAutorizacaoBancaria(usuarioId, accessToken);

            log.info("✅ Token de PRODUÇÃO gerado com sucesso para usuário: {}", usuarioId);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Token de PRODUÇÃO gerado com sucesso",
                "usuarioId", usuarioId,
                "accessToken", accessToken.substring(0, 10) + "...",
                "timestamp", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("❌ Erro ao gerar token de PRODUÇÃO para usuário {}: {}", usuarioId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Erro interno: " + e.getMessage(),
                "usuarioId", usuarioId
            ));
        }
    }

    /**
     * Gera Access Token de PRODUÇÃO usando Client Credentials
     */
    private String gerarAccessTokenProducao(String clientId, String clientSecret) {
        try {
            log.info("🔑 Gerando Access Token de PRODUÇÃO...");

            String tokenUrl = "https://api.mercadopago.com/oauth/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("scope", "read,write");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.info("📡 Fazendo requisição para: {}", tokenUrl);
            log.info("🔑 Client ID: {}", clientId);
            log.info("🔐 Client Secret: {}...", clientSecret.substring(0, Math.min(8, clientSecret.length())));

            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl, 
                org.springframework.http.HttpMethod.POST, 
                request, 
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String accessToken = (String) responseBody.get("access_token");
                String tokenType = (String) responseBody.get("token_type");
                Integer expiresIn = (Integer) responseBody.get("expires_in");
                
                log.info("✅ Token de PRODUÇÃO gerado com sucesso!");
                log.info("🔑 Access Token: {}...", accessToken.substring(0, 10));
                log.info("📝 Token Type: {}", tokenType);
                log.info("⏰ Expires In: {} segundos", expiresIn);
                
                return accessToken;
            } else {
                log.error("❌ Falha ao gerar token de PRODUÇÃO. Status: {}", response.getStatusCode());
                log.error("❌ Response Body: {}", response.getBody());
                return null;
            }

        } catch (Exception e) {
            log.error("❌ Erro ao gerar Access Token de PRODUÇÃO: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Atualiza a autorização bancária com o novo token
     */
    private void atualizarAutorizacaoBancaria(Long usuarioId, String accessToken) {
        try {
            log.info("🔄 Atualizando autorização bancária para usuário: {}", usuarioId);

            // Buscar autorização existente
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");

            if (authOpt.isPresent()) {
                // Atualizar autorização existente
                AutorizacaoBancaria auth = authOpt.get();
                auth.setAccessToken(accessToken);
                auth.setDataAtualizacao(LocalDateTime.now());
                auth.setDataExpiracao(LocalDateTime.now().plusHours(6)); // Token de produção expira em 6 horas
                auth.setAtivo(true);
                autorizacaoBancariaRepository.save(auth);
                
                log.info("✅ Autorização bancária atualizada com sucesso");
            } else {
                // Criar nova autorização
                Optional<Usuario> usuarioOpt = usuarioRepository.findById(usuarioId);
                if (usuarioOpt.isPresent()) {
                    AutorizacaoBancaria novaAuth = new AutorizacaoBancaria();
                    novaAuth.setUsuario(usuarioOpt.get());
                    novaAuth.setTipoBanco("MERCADO_PAGO");
                    novaAuth.setBanco("MERCADO_PAGO");
                    novaAuth.setTipoConta("CREDITO");
                    novaAuth.setAccessToken(accessToken);
                    novaAuth.setTokenType("Bearer");
                    novaAuth.setScope("read,write");
                    novaAuth.setAtivo(true);
                    novaAuth.setDataCriacao(LocalDateTime.now());
                    novaAuth.setDataAtualizacao(LocalDateTime.now());
                    novaAuth.setDataExpiracao(LocalDateTime.now().plusHours(6));
                    autorizacaoBancariaRepository.save(novaAuth);
                    
                    log.info("✅ Nova autorização bancária criada com sucesso");
                } else {
                    log.error("❌ Usuário não encontrado: {}", usuarioId);
                }
            }

        } catch (Exception e) {
            log.error("❌ Erro ao atualizar autorização bancária: {}", e.getMessage(), e);
        }
    }

    /**
     * Testa o token de PRODUÇÃO gerado
     */
    @GetMapping("/testar-producao")
    public ResponseEntity<Map<String, Object>> testarTokenProducao(@RequestParam Long usuarioId) {
        try {
            log.info("🧪 Testando token de PRODUÇÃO para usuário: {}", usuarioId);

            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");

            if (authOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Autorização bancária não encontrada",
                    "usuarioId", usuarioId
                ));
            }

            AutorizacaoBancaria auth = authOpt.get();
            String accessToken = auth.getAccessToken();

            // Testar o token fazendo uma chamada à API
            String testUrl = "https://api.mercadopago.com/v1/payments/search?limit=1";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(headers);

            log.info("📡 Testando token: {}...", accessToken.substring(0, 10));

            ResponseEntity<Map> response = restTemplate.exchange(
                testUrl, 
                org.springframework.http.HttpMethod.GET, 
                request, 
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Token de PRODUÇÃO funcionando corretamente!");
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Token de PRODUÇÃO funcionando corretamente",
                    "usuarioId", usuarioId,
                    "response", response.getBody()
                ));
            } else {
                log.error("❌ Token de PRODUÇÃO não funcionou. Status: {}", response.getStatusCode());
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Token de PRODUÇÃO não funcionou",
                    "status", response.getStatusCode().value(),
                    "usuarioId", usuarioId
                ));
            }

        } catch (Exception e) {
            log.error("❌ Erro ao testar token de PRODUÇÃO: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Erro ao testar token: " + e.getMessage(),
                "usuarioId", usuarioId
            ));
        }
    }
}

