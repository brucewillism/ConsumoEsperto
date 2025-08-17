package com.consumoesperto.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidade que representa um usuário do sistema ConsumoEsperto
 * 
 * Esta classe define a estrutura de dados para um usuário, incluindo
 * informações de autenticação, dados pessoais e relacionamentos com
 * outras entidades como transações, categorias e cartões de crédito.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "usuarios") // Nome da tabela no banco de dados
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class Usuario {

    /**
     * Identificador único do usuário (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nome de usuário único para login
     * Deve ser preenchido e ter no máximo 50 caracteres
     */
    @Size(max = 50, message = "Nome de usuário deve ter no máximo 50 caracteres")
    @Column(unique = true) // Garante que o username seja único
    private String username;

    /**
     * Senha criptografada do usuário (opcional para OAuth2)
     * Deve ter no máximo 120 caracteres
     */
    @Size(max = 120, message = "Senha deve ter no máximo 120 caracteres")
    private String password;

    /**
     * Email único do usuário
     * Deve ser um email válido e ter no máximo 50 caracteres
     */
    @NotBlank(message = "Email é obrigatório")
    @Size(max = 50, message = "Email deve ter no máximo 50 caracteres")
    @Email(message = "Formato de email inválido")
    @Column(unique = true) // Garante que o email seja único
    private String email;

    /**
     * Nome completo do usuário
     * Deve ser preenchido e ter no máximo 100 caracteres
     */
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    /**
     * Data e hora de criação do usuário no sistema
     * Preenchida automaticamente quando o usuário é criado
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    /**
     * Data e hora do último acesso do usuário ao sistema
     * Atualizada a cada login
     */
    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    /**
     * ID único do usuário no Google (OAuth2)
     * Usado para identificar usuários que fazem login via Google
     */
    @Column(name = "google_id", unique = true)
    private String googleId;

    /**
     * URL da foto de perfil do usuário no Google
     */
    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    /**
     * Locale/idioma preferido do usuário
     */
    @Column(name = "locale", length = 10)
    private String locale;

    /**
     * Indica se a conta do usuário foi verificada pelo Google
     */
    @Column(name = "email_verificado")
    private Boolean emailVerificado = false;

    /**
     * Provedor de autenticação (GOOGLE, LOCAL, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provedor_auth")
    private ProvedorAuth provedorAuth = ProvedorAuth.LOCAL;

    /**
     * Lista de transações financeiras do usuário
     * Relacionamento um-para-muitos: um usuário pode ter várias transações
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Transacao> transacoes = new HashSet<>();

    /**
     * Lista de categorias personalizadas do usuário
     * Relacionamento um-para-muitos: um usuário pode ter várias categorias
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Categoria> categorias = new HashSet<>();

    /**
     * Lista de cartões de crédito do usuário
     * Relacionamento um-para-muitos: um usuário pode ter vários cartões
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<CartaoCredito> cartoesCredito = new HashSet<>();

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define a data de criação quando um novo usuário é criado
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }

    /**
     * Método executado automaticamente antes de atualizar a entidade
     * Atualiza o último acesso quando o usuário faz login
     */
    @PreUpdate
    protected void onUpdate() {
        ultimoAcesso = LocalDateTime.now();
    }

    /**
     * Enum para tipos de provedor de autenticação
     */
    public enum ProvedorAuth {
        LOCAL,      // Login local com senha
        GOOGLE,     // Login via Google OAuth2
        MERCADOPAGO // Login via Mercado Pago (futuro)
    }
}
