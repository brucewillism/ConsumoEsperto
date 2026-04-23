package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * DTO para resposta de autenticação OAuth2
 * 
 * Este DTO contém os dados retornados após autenticação bem-sucedida,
 * incluindo o token JWT e informações do usuário.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    /**
     * Token JWT para autenticação
     */
    private String token;

    /**
     * Tipo do token (Bearer)
     */
    private String tokenType = "Bearer";

    /**
     * Tempo de expiração do token em milissegundos
     */
    private Long expiresIn;

    /**
     * Informações do usuário autenticado
     */
    private UserInfo user;

    /**
     * Data e hora da autenticação
     */
    private LocalDateTime authenticatedAt;

    /**
     * DTO interno para informações do usuário
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String nome;
        private String fotoUrl;
        private String googleId;
        private String provedorAuth;
        private LocalDateTime dataCriacao;
        private LocalDateTime ultimoAcesso;
    }
}
