package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

/**
 * Entidade que representa a configuração de uma API bancária
 * 
 * Esta classe armazena as configurações necessárias para conectar
 * com APIs bancárias específicas, incluindo URLs, credenciais
 * e parâmetros de conexão.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "bank_api_configs")
@NoArgsConstructor
@AllArgsConstructor
public class BankApiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

<<<<<<< HEAD
    @NotBlank(message = "Nome da configuração é obrigatório")
    @Column(name = "nome")
    private String nome;

    @NotBlank(message = "Tipo do banco é obrigatório")
    @Column(name = "tipo_banco")
    private String tipoBanco;

    @NotBlank(message = "URL da API é obrigatória")
    @Column(name = "api_url", length = 500)
=======
    // @Column(name = "bank_name", nullable = false)
    // private String bankName;

    @Column(name = "banco", nullable = false)
    private String banco; // ITAU, MERCADOPAGO, NUBANK, INTER

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    // @Column(name = "user_id")
    // private String userId;

    @Column(name = "api_url", nullable = false)
>>>>>>> origin/main
    private String apiUrl;

    @Column(name = "client_id", length = 200)
    private String clientId;

    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(name = "scope")
    private String scope;

<<<<<<< HEAD
    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;
=======
    @Column(name = "sandbox")
    private Boolean sandbox = false;

    @Column(name = "ativo")
    private Boolean ativo = true;
>>>>>>> origin/main

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-bank-configs")
    private Usuario usuario;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

<<<<<<< HEAD
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipoBanco() { return tipoBanco; }
    public void setTipoBanco(String tipoBanco) { this.tipoBanco = tipoBanco; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    // Métodos de compatibilidade para código existente
    public String getBanco() { return tipoBanco; }
    public void setBanco(String banco) { this.tipoBanco = banco; }
    
    public String getAuthUrl() { return apiUrl; }
    public void setAuthUrl(String authUrl) { this.apiUrl = authUrl; }
    
    public String getTokenUrl() { return apiUrl; }
    public void setTokenUrl(String tokenUrl) { this.apiUrl = tokenUrl; }
    
    public String getUserId() { return null; } // Campo removido, retorna null para compatibilidade
    public void setUserId(String userId) { /* Campo removido */ }
    
    // Builder pattern para compatibilidade
    public static BankApiConfigBuilder builder() {
        return new BankApiConfigBuilder();
    }
    
    public static class BankApiConfigBuilder {
        private BankApiConfig config = new BankApiConfig();
        
        public BankApiConfigBuilder id(Long id) { config.setId(id); return this; }
        public BankApiConfigBuilder nome(String nome) { config.setNome(nome); return this; }
        public BankApiConfigBuilder tipoBanco(String tipoBanco) { config.setTipoBanco(tipoBanco); return this; }
        public BankApiConfigBuilder banco(String banco) { config.setTipoBanco(banco); return this; } // Método de compatibilidade
        public BankApiConfigBuilder apiUrl(String apiUrl) { config.setApiUrl(apiUrl); return this; }
        public BankApiConfigBuilder authUrl(String authUrl) { config.setApiUrl(authUrl); return this; } // Método de compatibilidade
        public BankApiConfigBuilder clientId(String clientId) { config.setClientId(clientId); return this; }
        public BankApiConfigBuilder clientSecret(String clientSecret) { config.setClientSecret(clientSecret); return this; }
        public BankApiConfigBuilder redirectUri(String redirectUri) { config.setRedirectUri(redirectUri); return this; }
        public BankApiConfigBuilder tokenUrl(String tokenUrl) { config.setApiUrl(tokenUrl); return this; } // Método de compatibilidade
        public BankApiConfigBuilder scope(String scope) { config.setScope(scope); return this; }
        public BankApiConfigBuilder ativo(Boolean ativo) { config.setAtivo(ativo); return this; }
        public BankApiConfigBuilder sandbox(Boolean sandbox) { /* Campo removido, ignora */ return this; } // Método de compatibilidade
        public BankApiConfigBuilder dataCriacao(LocalDateTime dataCriacao) { config.setDataCriacao(dataCriacao); return this; }
        public BankApiConfigBuilder dataAtualizacao(LocalDateTime dataAtualizacao) { config.setDataAtualizacao(dataAtualizacao); return this; }
        public BankApiConfigBuilder usuario(Usuario usuario) { config.setUsuario(usuario); return this; }
        public BankApiConfigBuilder userId(String userId) { /* Campo removido, ignora */ return this; } // Método de compatibilidade
        public BankApiConfigBuilder timeoutMs(Integer timeoutMs) { /* Campo removido, ignora */ return this; } // Método de compatibilidade
        public BankApiConfigBuilder maxRetries(Integer maxRetries) { /* Campo removido, ignora */ return this; } // Método de compatibilidade
        public BankApiConfigBuilder retryDelayMs(Integer retryDelayMs) { /* Campo removido, ignora */ return this; } // Método de compatibilidade
        
        public BankApiConfig build() { return config; }
    }
    
    // Métodos de compatibilidade para campos removidos
    public Boolean getSandbox() { return false; } // Campo removido, retorna false para compatibilidade
    public void setSandbox(Boolean sandbox) { /* Campo removido */ }
    
    public Integer getTimeoutMs() { return 30000; } // Campo removido, retorna valor padrão para compatibilidade
    public void setTimeoutMs(Integer timeoutMs) { /* Campo removido */ }
    
    public Integer getMaxRetries() { return 3; } // Campo removido, retorna valor padrão para compatibilidade
    public void setMaxRetries(Integer maxRetries) { /* Campo removido */ }
    
    public Integer getRetryDelayMs() { return 1000; } // Campo removido, retorna valor padrão para compatibilidade
    public void setRetryDelayMs(Integer retryDelayMs) { /* Campo removido */ }
=======
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    // @Column(name = "last_test_at")
    // private LocalDateTime lastTestAt;

    // @Column(name = "last_test_status")
    // private String lastTestStatus; // SUCCESS, FAILED, NOT_TESTED

    // @Column(name = "last_test_message")
    // private String lastTestMessage;
>>>>>>> origin/main

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}
