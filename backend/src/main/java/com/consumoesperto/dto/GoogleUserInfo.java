package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO para informações do usuário retornadas pelo Google OAuth2
 * 
 * Este DTO contém os dados que o Google retorna após autenticação
 * bem-sucedida, incluindo informações básicas do perfil do usuário.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserInfo {

    /**
     * ID único do usuário no Google
     */
    private String id;

    /**
     * Email do usuário
     */
    private String email;

    /**
     * Nome completo do usuário
     */
    private String name;

    /**
     * Nome de usuário (username)
     */
    private String given_name;

    /**
     * Sobrenome do usuário
     */
    private String family_name;

    /**
     * URL da foto de perfil
     */
    private String picture;

    /**
     * Locale/idioma preferido
     */
    private String locale;

    /**
     * Indica se o email foi verificado
     */
    private Boolean verified_email;

    /**
     * Token de acesso OAuth2
     */
    private String access_token;

    /**
     * Token de refresh OAuth2
     */
    private String refresh_token;

    /**
     * Escopo das permissões concedidas
     */
    private String scope;

    /**
     * Tipo do token
     */
    private String token_type;

    /**
     * Tempo de expiração do token em segundos
     */
    private Integer expires_in;
}
