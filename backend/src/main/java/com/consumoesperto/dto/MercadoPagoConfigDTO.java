package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

<<<<<<< HEAD
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.validation.constraints.Pattern;

=======
>>>>>>> origin/main
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
<<<<<<< HEAD
    @NotBlank(message = "Access Token é obrigatório")
    @Size(min = 20, max = 500, message = "Access Token deve ter entre 20 e 500 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Access Token contém caracteres inválidos")
=======
>>>>>>> origin/main
    private String accessToken;

    /**
     * Chave pública da aplicação
     * Usada no frontend para criptografia e identificação
     */
<<<<<<< HEAD
    @NotBlank(message = "Public Key é obrigatória")
    @Size(min = 20, max = 200, message = "Public Key deve ter entre 20 e 200 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Public Key contém caracteres inválidos")
=======
>>>>>>> origin/main
    private String publicKey;

    /**
     * Client ID da aplicação
     * Identificador único da integração
     */
<<<<<<< HEAD
    @NotBlank(message = "Client ID é obrigatório")
    @Size(min = 10, max = 100, message = "Client ID deve ter entre 10 e 100 caracteres")
    @Pattern(regexp = "^[0-9]+$", message = "Client ID deve conter apenas números")
=======
>>>>>>> origin/main
    private String clientId;

    /**
     * Client Secret da aplicação
     * Chave privada para autenticação OAuth
     */
<<<<<<< HEAD
    @NotBlank(message = "Client Secret é obrigatório")
    @Size(min = 20, max = 200, message = "Client Secret deve ter entre 20 e 200 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "Client Secret contém caracteres inválidos")
=======
>>>>>>> origin/main
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
