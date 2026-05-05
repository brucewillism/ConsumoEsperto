package com.consumoesperto.model;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonManagedReference;

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
     * Nome de usuário único para login no sistema
     * Deve ser único e ter no máximo 50 caracteres
     */
    @NotBlank(message = "Username é obrigatório")
    @Size(max = 50, message = "Username deve ter no máximo 50 caracteres")
    @Column(unique = true)
    private String username;

    /**
     * Senha criptografada do usuário
     * Armazenada de forma segura usando BCrypt
     * Obrigatória apenas para usuários com autenticação LOCAL
     */
    @Size(min = 6, max = 120, message = "Senha deve ter entre 6 e 120 caracteres")
    private String password;

    /**
     * Endereço de email único do usuário
     * Deve ser único e seguir formato válido de email
     */
    @NotBlank(message = "Email é obrigatório")
    @Size(max = 50, message = "Email deve ter no máximo 50 caracteres")
    @Email(message = "Email deve ser válido")
    @Column(unique = true)
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
     * Numero de WhatsApp vinculado ao usuario em formato E.164 (ex: +5511999999999)
     */
    @Column(name = "whatsapp_number", unique = true, length = 20)
    private String whatsappNumero;

    /**
     * Lista de transações financeiras do usuário
     * Relacionamento um-para-muitos: um usuário pode ter várias transações
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("usuario-transacoes")
    private Set<Transacao> transacoes = new HashSet<>();

    /**
     * Lista de categorias personalizadas do usuário
     * Relacionamento um-para-muitos: um usuário pode ter várias categorias
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("usuario-categorias")
    private Set<Categoria> categorias = new HashSet<>();

    /**
     * Lista de cartões de crédito do usuário
     * Relacionamento um-para-muitos: um usuário pode ter vários cartões
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("usuario-cartoes")
    private Set<CartaoCredito> cartoesCredito = new HashSet<>();

    /**
     * Lista de compras parceladas do usuário
     * Relacionamento um-para-muitos: um usuário pode ter várias compras parceladas
     * Carregamento lazy para melhor performance
     */
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference("usuario-compras")
    private Set<CompraParcelada> comprasParceladas = new HashSet<>();

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getUltimoAcesso() { return ultimoAcesso; }
    public void setUltimoAcesso(LocalDateTime ultimoAcesso) { this.ultimoAcesso = ultimoAcesso; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public Boolean getEmailVerificado() { return emailVerificado; }
    public void setEmailVerificado(Boolean emailVerificado) { this.emailVerificado = emailVerificado; }

    public ProvedorAuth getProvedorAuth() { return provedorAuth; }
    public void setProvedorAuth(ProvedorAuth provedorAuth) { this.provedorAuth = provedorAuth; }

    public String getWhatsappNumero() { return whatsappNumero; }
    public void setWhatsappNumero(String whatsappNumero) { this.whatsappNumero = whatsappNumero; }

    public Set<Transacao> getTransacoes() { return transacoes; }
    public void setTransacoes(Set<Transacao> transacoes) { this.transacoes = transacoes; }

    public Set<Categoria> getCategorias() { return categorias; }
    public void setCategorias(Set<Categoria> categorias) { this.categorias = categorias; }

    public Set<CartaoCredito> getCartoesCredito() { return cartoesCredito; }
    public void setCartoesCredito(Set<CartaoCredito> cartoesCredito) { this.cartoesCredito = cartoesCredito; }

    public Set<CompraParcelada> getComprasParceladas() { return comprasParceladas; }
    public void setComprasParceladas(Set<CompraParcelada> comprasParceladas) { this.comprasParceladas = comprasParceladas; }

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define a data de criação quando um novo usuário é criado
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }

    /**
     * Enum que define os provedores de autenticação suportados
     */
    public enum ProvedorAuth {
        LOCAL,   // Autenticação local com username/password
        GOOGLE   // Autenticação via Google OAuth2
    }
}
