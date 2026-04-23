package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;

    /**
     * Autentica um usuário e retorna o token JWT
     */
    public Map<String, Object> authenticate(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
            );

            String token = jwtTokenProvider.generateToken(authentication);
            
            // Buscar dados do usuário
            Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
            Usuario usuario = usuarioOpt.orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            return Map.of(
                "status", "success",
                "token", token,
                "usuario", Map.of(
                    "id", usuario.getId(),
                    "nome", usuario.getNome(),
                    "email", usuario.getEmail()
                )
            );
        } catch (Exception e) {
            log.error("Erro na autenticação: {}", e.getMessage());
            return Map.of(
                "status", "error",
                "message", "Credenciais inválidas"
            );
        }
    }

    /**
     * Valida um token JWT
     */
    public boolean validateToken(String token) {
        try {
            return jwtTokenProvider.validateToken(token);
        } catch (Exception e) {
            log.error("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtém o usuário atual do contexto de segurança
     */
    public Usuario getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        }
        throw new RuntimeException("Usuário não autenticado");
    }

    /**
     * Obtém o ID do usuário atual
     */
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
