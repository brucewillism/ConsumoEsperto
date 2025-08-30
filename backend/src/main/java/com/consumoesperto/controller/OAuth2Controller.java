package com.consumoesperto.controller;

import com.consumoesperto.dto.AuthResponse;
<<<<<<< HEAD
import com.consumoesperto.dto.GoogleTokenRequest;
=======
>>>>>>> origin/main
import com.consumoesperto.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * Controller para autenticação OAuth2
 * 
 * Este controller gerencia os endpoints de autenticação OAuth2,
 * incluindo login com Google e callback de autenticação.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
<<<<<<< HEAD
@RestController
=======
// @RestController
>>>>>>> origin/main
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    /**
     * Endpoint para login OAuth2 com Google
     * 
<<<<<<< HEAD
     * Aceita dados de login Google via body da requisição
     * 
     * @param tokenRequest DTO contendo o token de acesso e informações do usuário
     * @return Resposta de autenticação com JWT e dados do usuário
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody GoogleTokenRequest tokenRequest) {
        try {
            log.info("🔐 Recebida requisição de login Google OAuth2");
            log.debug("Token recebido: {}", tokenRequest.getAccessToken() != null ? "Presente" : "Ausente");

            if (tokenRequest.getAccessToken() == null || tokenRequest.getAccessToken().trim().isEmpty()) {
                log.error("❌ Token de acesso não fornecido");
                return ResponseEntity.badRequest().body(null);
            }

            AuthResponse response = oAuth2Service.processGoogleOAuth2(tokenRequest.getAccessToken());
=======
     * @param request Requisição HTTP contendo o token de acesso
     * @return Resposta de autenticação com JWT e dados do usuário
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(HttpServletRequest request) {
        try {
            // Extrair token do header Authorization
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().build();
            }

            String accessToken = authHeader.substring(7); // Remove "Bearer "
            log.info("🔐 Recebida requisição de login Google OAuth2");

            AuthResponse response = oAuth2Service.processGoogleOAuth2(accessToken);
>>>>>>> origin/main
            log.info("✅ Login Google OAuth2 processado com sucesso para: {}", response.getUser().getEmail());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro no login Google OAuth2: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint para login OAuth2 com Google (alternativo via body)
     * 
     * @param accessToken Token de acesso do Google
     * @return Resposta de autenticação com JWT e dados do usuário
     */
    @PostMapping("/google/token")
    public ResponseEntity<AuthResponse> googleLoginWithToken(@RequestBody String accessToken) {
        try {
            log.info("🔐 Recebida requisição de login Google OAuth2 com token");

            AuthResponse response = oAuth2Service.processGoogleOAuth2(accessToken);
            log.info("✅ Login Google OAuth2 processado com sucesso para: {}", response.getUser().getEmail());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erro no login Google OAuth2: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
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
    public ResponseEntity<String> googleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {
        
        log.info("🔄 Callback Google OAuth2 recebido - Code: {}, State: {}", code, state);
        
        // TODO: Implementar troca de código por token de acesso
        // Por enquanto, retorna instruções para o frontend
        String instructions = """
            Callback OAuth2 recebido com sucesso!
            
            Para completar a autenticação, o frontend deve:
            1. Trocar o código de autorização por um token de acesso
            2. Enviar o token para /api/auth/google ou /api/auth/google/token
            
            Código recebido: %s
            """.formatted(code);
        
        return ResponseEntity.ok(instructions);
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
