package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO para configuração de credenciais do Mercado Pago
 * 
 * Este DTO contém os dados necessários para configurar
 * a integração com a API do Mercado Pago.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MercadoPagoConfigDTO {

    /**
     * Token de acesso do Mercado Pago
     * Usado para autenticar requisições à API
     */
    private String accessToken;

    /**
     * Chave pública da aplicação
     * Usada no frontend para criptografia e identificação
     */
    private String publicKey;

    /**
     * Client ID da aplicação
     * Identificador único da integração
     */
    private String clientId;

    /**
     * Client Secret da aplicação
     * Chave privada para autenticação OAuth
     */
    private String clientSecret;

    /**
     * ID do usuário no Mercado Pago (opcional)
     * Pode ser usado para identificação adicional
     */
    private String userId;

    /**
     * Indica se as credenciais estão ativas
     */
    private Boolean ativo = true;
}
