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

    @NotBlank(message = "Nome da configuração é obrigatório")
    @Column(name = "nome")
    private String nome;

    @NotBlank(message = "Tipo do banco é obrigatório")
    @Column(name = "tipo_banco")
    private String tipoBanco;

    @NotBlank(message = "Banco é obrigatório")
    @Column(name = "banco", nullable = false)
    private String banco;

    @NotBlank(message = "URL da API é obrigatória")
    @Column(name = "api_url", length = 500)
    private String apiUrl;

    @Column(name = "client_id", length = 200)
    private String clientId;

    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(name = "scope")
    private String scope;

    @Column(name = "auth_url", length = 500)
    private String authUrl;

    @Column(name = "token_url", length = 500)
    private String tokenUrl;

    @Column(name = "sandbox")
    private Boolean sandbox = false;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 30000;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "retry_delay_ms")
    private Integer retryDelayMs = 1000;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-bank-configs")
    private Usuario usuario;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipoBanco() { return tipoBanco; }
    public void setTipoBanco(String tipoBanco) { this.tipoBanco = tipoBanco; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

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

    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }

    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }

    public Boolean getSandbox() { return sandbox; }
    public void setSandbox(Boolean sandbox) { this.sandbox = sandbox; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public Integer getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(Integer retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    // Métodos de compatibilidade para código existente
    // (Removidos pois agora são campos reais)
    
    // Builder pattern para compatibilidade
    public static BankApiConfigBuilder builder() {
        return new BankApiConfigBuilder();
    }
    
    public static class BankApiConfigBuilder {
        private BankApiConfig config = new BankApiConfig();
        
        public BankApiConfigBuilder id(Long id) { config.setId(id); return this; }
        public BankApiConfigBuilder nome(String nome) { config.setNome(nome); return this; }
        public BankApiConfigBuilder tipoBanco(String tipoBanco) { config.setTipoBanco(tipoBanco); return this; }
        public BankApiConfigBuilder banco(String banco) { config.setBanco(banco); return this; }
        public BankApiConfigBuilder apiUrl(String apiUrl) { config.setApiUrl(apiUrl); return this; }
        public BankApiConfigBuilder authUrl(String authUrl) { config.setAuthUrl(authUrl); return this; }
        public BankApiConfigBuilder clientId(String clientId) { config.setClientId(clientId); return this; }
        public BankApiConfigBuilder clientSecret(String clientSecret) { config.setClientSecret(clientSecret); return this; }
        public BankApiConfigBuilder redirectUri(String redirectUri) { config.setRedirectUri(redirectUri); return this; }
        public BankApiConfigBuilder tokenUrl(String tokenUrl) { config.setTokenUrl(tokenUrl); return this; }
        public BankApiConfigBuilder scope(String scope) { config.setScope(scope); return this; }
        public BankApiConfigBuilder ativo(Boolean ativo) { config.setAtivo(ativo); return this; }
        public BankApiConfigBuilder sandbox(Boolean sandbox) { config.setSandbox(sandbox); return this; }
        public BankApiConfigBuilder dataCriacao(LocalDateTime dataCriacao) { config.setDataCriacao(dataCriacao); return this; }
        public BankApiConfigBuilder dataAtualizacao(LocalDateTime dataAtualizacao) { config.setDataAtualizacao(dataAtualizacao); return this; }
        public BankApiConfigBuilder usuario(Usuario usuario) { config.setUsuario(usuario); return this; }
        public BankApiConfigBuilder userId(String userId) { config.setUserId(userId); return this; }
        public BankApiConfigBuilder timeoutMs(Integer timeoutMs) { config.setTimeoutMs(timeoutMs); return this; }
        public BankApiConfigBuilder maxRetries(Integer maxRetries) { config.setMaxRetries(maxRetries); return this; }
        public BankApiConfigBuilder retryDelayMs(Integer retryDelayMs) { config.setRetryDelayMs(retryDelayMs); return this; }
        
        public BankApiConfig build() { return config; }
    }
    
    // Métodos de compatibilidade para campos removidos
    // (Removidos pois agora são campos reais)

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
