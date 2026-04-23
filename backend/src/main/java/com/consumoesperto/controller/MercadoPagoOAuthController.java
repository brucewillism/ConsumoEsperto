package com.consumoesperto.controller;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.service.RealMercadoPagoTokenService;
import com.consumoesperto.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Controller para gerenciar OAuth2 do Mercado Pago
 * 
 * Este controller fornece endpoints para iniciar o fluxo OAuth2
 * e processar callbacks de autorização do Mercado Pago.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/mercadopago/oauth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mercado Pago OAuth2", description = "Endpoints para autorização OAuth2 do Mercado Pago")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
public class MercadoPagoOAuthController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final RealMercadoPagoTokenService realTokenService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Inicia o fluxo OAuth2 do Mercado Pago
     * 
     * Gera a URL de autorização do Mercado Pago para que o usuário
     * possa autorizar o acesso aos seus dados.
     * 
     * @param currentUser Usuário autenticado
     * @return URL de autorização do Mercado Pago
     */
    @GetMapping("/authorize")
    @Operation(summary = "Iniciar autorização OAuth2", description = "Gera URL para autorização OAuth2 do Mercado Pago")
    public ResponseEntity<Map<String, Object>> authorize(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("🔐 Iniciando fluxo OAuth2 do Mercado Pago para usuário: {}", currentUser.getId());
            
            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(currentUser.getId(), "MERCADO_PAGO");
            
            if (!configOpt.isPresent()) {
                log.warn("⚠️ Configuração do Mercado Pago não encontrada para usuário: {}", currentUser.getId());
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Configuração do Mercado Pago não encontrada"));
            }
            
            BankApiConfig config = configOpt.get();
            
            if (!config.getAtivo()) {
                log.warn("⚠️ Configuração do Mercado Pago inativa para usuário: {}", currentUser.getId());
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Configuração do Mercado Pago inativa"));
            }
            
            // Construir URL de autorização
            String authUrl = buildAuthorizationUrl(config);
            
            log.info("✅ URL de autorização gerada: {}", authUrl);
            
            Map<String, Object> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("message", "Acesse a URL para autorizar o acesso aos seus dados do Mercado Pago");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao gerar URL de autorização: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erro interno do servidor"));
        }
    }

    /**
     * Processa o callback de autorização do Mercado Pago
     * 
     * Este endpoint é chamado pelo Mercado Pago após o usuário
     * autorizar o acesso aos seus dados.
     * 
     * @param code Código de autorização retornado pelo Mercado Pago
     * @param state Estado da requisição (opcional)
     * @return Resultado da autorização
     */
    @GetMapping("/callback")
    @Operation(summary = "Processar callback OAuth2", description = "Processa callback de autorização do Mercado Pago")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {

        try {
            log.info("🔄 Processando callback OAuth2 do Mercado Pago. Code: {}", code);

            // Trocar código por access_token real
            Map<String, Object> tokenResponse = trocarCodigoPorToken(code);

            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                log.info("✅ Token real obtido com sucesso");
                
                // Salvar autorização no banco
                boolean saved = realTokenService.saveNewAuthorization(1L, tokenResponse);
                
                if (saved) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Autorização processada e salva com sucesso");
                    response.put("access_token", tokenResponse.get("access_token"));
                    response.put("refresh_token", tokenResponse.get("refresh_token"));
                    response.put("expires_in", tokenResponse.get("expires_in"));
                    response.put("scope", tokenResponse.get("scope"));

                    return ResponseEntity.ok(response);
                } else {
                    log.error("❌ Falha ao salvar autorização");
                    return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Falha ao salvar autorização"));
                }
            } else {
                log.error("❌ Falha ao obter token real");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Falha ao obter token de acesso"));
            }

        } catch (Exception e) {
            log.error("❌ Erro ao processar callback: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erro ao processar autorização"));
        }
    }

    /**
     * Endpoint público para iniciar OAuth2 (sem autenticação)
     * 
     * @return URL de autorização do Mercado Pago
     */
    @GetMapping("/public/authorize")
    @Operation(summary = "Iniciar autorização OAuth2 (Público)", description = "Gera URL para autorização do Mercado Pago sem autenticação")
    public ResponseEntity<Map<String, Object>> authorizePublic() {
        try {
            log.info("🔐 Iniciando fluxo OAuth2 público do Mercado Pago");

            // Buscar configuração do Mercado Pago (usuário 1)
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByUsuarioIdAndTipoBanco(1L, "MERCADO_PAGO");
            
            if (configOpt.isEmpty()) {
                log.error("❌ Configuração do Mercado Pago não encontrada");
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Configuração do Mercado Pago não encontrada"));
            }

            BankApiConfig config = configOpt.get();
            
            // Gerar URL de autorização
            String authUrl = buildAuthorizationUrl(config);
            
            log.info("✅ URL de autorização pública gerada: {}", authUrl);

            Map<String, Object> response = new HashMap<>();
            response.put("authorization_url", authUrl);
            response.put("message", "Acesse a URL para autorizar o acesso ao Mercado Pago");
            response.put("instructions", "Após autorizar, você será redirecionado de volta para a aplicação");
            response.put("callback_url", "https://0d723f1e294f.ngrok-free.app/api/bank/oauth/mercadopago/callback");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro ao gerar URL de autorização pública: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erro ao gerar URL de autorização"));
        }
    }

    /**
     * Constrói a URL de autorização do Mercado Pago
     * 
     * @param config Configuração do Mercado Pago
     * @return URL de autorização completa
     */
    private String buildAuthorizationUrl(BankApiConfig config) {
        StringBuilder url = new StringBuilder();
        url.append(config.getAuthUrl());
        url.append("?response_type=code");
        url.append("&client_id=").append(URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8));
        url.append("&redirect_uri=").append(URLEncoder.encode(config.getRedirectUri(), StandardCharsets.UTF_8));
        url.append("&scope=").append(URLEncoder.encode(config.getScope(), StandardCharsets.UTF_8));
        url.append("&state=").append(URLEncoder.encode("mercadopago_oauth", StandardCharsets.UTF_8));
        
        return url.toString();
    }

    /**
     * Troca o código de autorização por um access_token real
     * 
     * @param code Código de autorização
     * @return Resposta com token de acesso
     */
    private Map<String, Object> trocarCodigoPorToken(String code) {
        try {
            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByUsuarioIdAndTipoBanco(1L, "MERCADO_PAGO");
            
            if (configOpt.isEmpty()) {
                log.error("❌ Configuração do Mercado Pago não encontrada");
                return null;
            }

            BankApiConfig config = configOpt.get();
            
            // URL do endpoint de token
            String tokenUrl = "https://api.mercadopago.com/oauth/token";
            
            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(config.getClientId(), config.getClientSecret());
            
            // Body da requisição
            String body = String.format(
                "grant_type=authorization_code&code=%s&redirect_uri=%s",
                code,
                URLEncoder.encode(config.getRedirectUri(), "UTF-8")
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            
            log.info("🔄 Fazendo requisição para trocar código por token...");
            log.info("   URL: {}", tokenUrl);
            log.info("   Client ID: {}", config.getClientId());
            log.info("   Redirect URI: {}", config.getRedirectUri());
            
            // Fazer requisição
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                log.info("✅ Token obtido com sucesso: {}", tokenData.keySet());
                return tokenData;
            } else {
                log.error("❌ Resposta inesperada: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao trocar código por token: {}", e.getMessage(), e);
            return null;
        }
    }
}
