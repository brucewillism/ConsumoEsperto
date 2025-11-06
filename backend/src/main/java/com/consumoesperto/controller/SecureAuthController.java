package com.consumoesperto.controller;

import com.consumoesperto.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/secure")
@RequiredArgsConstructor
@Slf4j
public class SecureAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
    public Map<String, Object> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");
        
        log.info("🔐 Tentativa de login para: {}", email);
        return authService.authenticate(email, password);
    }

    @GetMapping("/me")
    @CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
    public Map<String, Object> getCurrentUser(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Map.of("error", "Token não fornecido");
            }

            String token = authHeader.substring(7);
            if (!authService.validateToken(token)) {
                return Map.of("error", "Token inválido");
            }

            var usuario = authService.getCurrentUser();
            return Map.of(
                "status", "success",
                "usuario", Map.of(
                    "id", usuario.getId(),
                    "nome", usuario.getNome(),
                    "email", usuario.getEmail()
                )
            );
        } catch (Exception e) {
            log.error("Erro ao obter usuário atual: {}", e.getMessage());
            return Map.of("error", "Erro interno do servidor");
        }
    }

    @PostMapping("/validate")
    @CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
    public Map<String, Object> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        
        if (token == null) {
            return Map.of("valid", false, "error", "Token não fornecido");
        }

        boolean isValid = authService.validateToken(token);
        return Map.of("valid", isValid);
    }
}
