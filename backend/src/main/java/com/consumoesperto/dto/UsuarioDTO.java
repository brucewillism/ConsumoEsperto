package com.consumoesperto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) para transferência de dados de usuário
 * 
 * Este DTO é usado para transferir dados de usuário entre as camadas
 * da aplicação, especialmente entre controllers e serviços. Inclui
 * validações para garantir a integridade dos dados recebidos.
 * 
 * Características:
 * - Validações de campos obrigatórios
 * - Restrições de tamanho para campos de texto
 * - Validação de formato de email
 * - Não expõe informações sensíveis como senha criptografada
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class UsuarioDTO {

    /**
     * Identificador único do usuário
     * Pode ser nulo para criação de novos usuários
     */
    private Long id;

    /**
     * Nome de usuário único para login no sistema
     * 
     * Validações:
     * - Obrigatório: não pode ser nulo ou vazio
     * - Tamanho máximo: 50 caracteres
     * - Deve ser único no sistema
     */
    @NotBlank(message = "Username é obrigatório")
    @Size(max = 50, message = "Username deve ter no máximo 50 caracteres")
    private String username;

    /**
     * Senha do usuário para autenticação
     * 
     * Validações:
     * - Obrigatória: não pode ser nula ou vazia
     * - Tamanho mínimo: 6 caracteres (segurança)
     * - Tamanho máximo: 120 caracteres (compatibilidade com hash)
     * 
     * Nota: A senha é criptografada antes de ser armazenada no banco
     */
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, max = 120, message = "Senha deve ter entre 6 e 120 caracteres")
    private String password;

    /**
     * Endereço de email único do usuário
     * 
     * Validações:
     * - Obrigatório: não pode ser nulo ou vazio
     * - Formato válido: deve seguir padrão de email
     * - Tamanho máximo: 50 caracteres
     * - Deve ser único no sistema
     */
    @NotBlank(message = "Email é obrigatório")
    @Size(max = 50, message = "Email deve ter no máximo 50 caracteres")
    @Email(message = "Email deve ser válido")
    private String email;

    /**
     * Nome completo do usuário
     * 
     * Validações:
     * - Obrigatório: não pode ser nulo ou vazio
     * - Tamanho máximo: 100 caracteres
     */
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    /**
     * URL da foto de perfil do usuário (opcional)
     * 
     * Pode ser nula para usuários sem foto de perfil.
     * Para usuários OAuth2 (Google), contém a URL da foto do provedor.
     */
    @Size(max = 500, message = "URL da foto deve ter no máximo 500 caracteres")
    private String fotoUrl;

    /**
     * Numero de WhatsApp vinculado em formato internacional (E.164)
     */
    @Size(max = 20, message = "Numero de WhatsApp deve ter no máximo 20 caracteres")
    private String whatsappNumero;

    /**
     * Data e hora de criação do usuário no sistema
     * Preenchida automaticamente pelo sistema
     */
    private LocalDateTime dataCriacao;

    /**
     * Data e hora do último acesso do usuário ao sistema
     * Atualizada automaticamente a cada login
     */
    private LocalDateTime ultimoAcesso;

    /**
     * Preferência de tratamento J.A.R.V.I.S. ({@code AUTOMATICO}, {@code SENHOR}, etc.).
     */
    private String preferenciaTratamentoJarvis;

    /**
     * Texto resumido para exibição (ex.: {@code Senhor João}), calculado no servidor.
     */
    private String jarvisTratamentoResumo;

    /** Valor de {@link com.consumoesperto.model.Usuario.GeneroUsuario} (ex.: MALE, FEMALE, UNKNOWN). */
    private String genero;

    /** Se o tratamento foi confirmado pela app ou ainda apenas inferência. */
    private Boolean generoConfirmado;

    /**
     * Título persistido (Senhor, Senhora, etc.) — vazio quando só o primeiro nome.
     */
    private String tratamento;

    /** Calibragem inicial concluída na aplicação. */
    private Boolean jarvisConfigurado;
}
