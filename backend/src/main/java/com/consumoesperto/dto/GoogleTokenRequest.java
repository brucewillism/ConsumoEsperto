package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO para requisição de login Google OAuth2
 * 
 * Este DTO contém o token de acesso obtido do Google OAuth2
 * que será usado para autenticar o usuário.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleTokenRequest {

    /**
     * Token de acesso do Google OAuth2
     */
    private String accessToken;

    /**
     * Informações do usuário (opcional, pode ser usado para cache)
     */
    private Object userInfo;
}
