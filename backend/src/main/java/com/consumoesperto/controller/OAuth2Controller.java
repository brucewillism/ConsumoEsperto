package com.consumoesperto.controller;

import com.consumoesperto.dto.AuthResponse;
import com.consumoesperto.dto.GoogleTokenRequest;
import com.consumoesperto.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller para autenticação OAuth2
 * 
 * Este controller gerencia os endpoints de autenticação OAuth2,
 * incluindo login com Google e callback de autenticação.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    /**
     * Endpoint para login OAuth2 com Google
     * 
     * Aceita dados de login Google via body da requisição
     * 
     * @param tokenRequest DTO contendo o token de acesso e informações do usuário
     * @return Resposta de autenticação com JWT e dados do usuário
     */
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleTokenRequest tokenRequest) {
        try {
            log.info("🔐 Recebida requisição de login Google OAuth2");
            log.debug("Token recebido: {}", tokenRequest.getAccessToken() != null ? "Presente" : "Ausente");

            if (tokenRequest.getAccessToken() == null || tokenRequest.getAccessToken().trim().isEmpty()) {
                log.error("❌ Token de acesso não fornecido");
                return ResponseEntity.badRequest().body(Map.of("message", "Token do Google não fornecido."));
            }

            AuthResponse response = oAuth2Service.processGoogleOAuth2(tokenRequest.getAccessToken());
            log.info("✅ Login Google OAuth2 processado com sucesso para: {}", response.getUser().getEmail());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro no login Google OAuth2: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Falha na autenticação Google. Gere um novo token e tente novamente."));
        }
    }

    /**
     * Endpoint para login OAuth2 com Google (alternativo via body)
     * 
     * @param accessToken Token de acesso do Google
     * @return Resposta de autenticação com JWT e dados do usuário
     */
    @PostMapping("/google/token")
    public ResponseEntity<?> googleLoginWithToken(@RequestBody String accessToken) {
        try {
            log.info("🔐 Recebida requisição de login Google OAuth2 com token");

            AuthResponse response = oAuth2Service.processGoogleOAuth2(accessToken);
            log.info("✅ Login Google OAuth2 processado com sucesso para: {}", response.getUser().getEmail());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro no login Google OAuth2: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Falha na autenticação Google. Token inválido ou expirado."));
        }
    }

    /**
     * Endpoint de callback para OAuth2 (para futuras implementações)
     * 
     * @param code Código de autorização do OAuth2
     * @param state Estado da requisição OAuth2
     * @return Resposta de autenticação
     */
    @GetMapping("/google/callback")
    public ResponseEntity<?> googleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {
        try {
            log.info("🔄 Callback Google OAuth2 recebido - State: {}", state);
            AuthResponse response = oAuth2Service.processGoogleOAuth2ByAuthorizationCode(code);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erro no callback OAuth2 Google: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Falha no callback OAuth2 Google"));
        }
    }

    /**
     * Endpoint de health check para OAuth2
     * 
     * @return Status do serviço OAuth2
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        log.info("🔍 Status OAuth2 verificado");
        return ResponseEntity.ok("OAuth2 Service está funcionando! 🚀");
    }
}
