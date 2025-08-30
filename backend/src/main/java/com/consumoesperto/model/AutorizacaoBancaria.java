package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

/**
 * Entidade que representa uma autorização bancária OAuth2
 * 
 * Esta classe armazena tokens de acesso e refresh para APIs bancárias,
 * permitindo que o sistema acesse dados financeiros do usuário de forma
 * segura e autorizada.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "autorizacoes_bancarias")
@NoArgsConstructor
@AllArgsConstructor
public class AutorizacaoBancaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

<<<<<<< HEAD
    @NotBlank(message = "Tipo do banco é obrigatório")
    @Column(name = "tipo_banco")
    private String tipoBanco;

    @NotBlank(message = "Token de acesso é obrigatório")
    @Column(name = "access_token", length = 2000)
    private String accessToken;

    @Column(name = "refresh_token", length = 2000)
    private String refreshToken;

    @NotNull(message = "Data de expiração é obrigatória")
    @Column(name = "data_expiracao")
    private LocalDateTime dataExpiracao;

    @Column(name = "scope")
    private String scope;

    @Column(name = "token_type")
    private String tokenType;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "data_criacao")
=======
    /**
     * Usuário que concedeu a autorização
     * Relacionamento muitos-para-um: um usuário pode ter várias autorizações
     */
    @NotNull(message = "Usuário é obrigatório")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Nome do banco para o qual a autorização foi concedida
     */
    @NotBlank(message = "Banco é obrigatório")
    @Column(name = "banco", nullable = false, length = 100)
    private String banco;

    /**
     * Tipo de conta bancária
     */
    @NotBlank(message = "Tipo de conta é obrigatório")
    @Column(name = "tipo_conta", nullable = false, length = 50)
    private String tipoConta;

    /**
     * Número da conta bancária
     */
    @Column(name = "numero_conta", length = 50)
    private String numeroConta;

    /**
     * Agência bancária
     */
    @Column(name = "agencia", length = 20)
    private String agencia;

    /**
     * Token de acesso OAuth2 para consultas à API do banco
     * Deve ser criptografado antes de ser armazenado por questões de segurança
     * Não pode ser nulo e deve ter tamanho adequado para tokens OAuth2
     */
    @Column(name = "token_acesso", columnDefinition = "TEXT")
    private String accessToken;

    /**
     * Refresh token OAuth2 para renovação automática do access token
     */
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    /**
     * Data e hora de expiração do token de acesso
     */
    @Column(name = "data_expiracao")
    private LocalDateTime dataExpiracao;

    /**
     * Indica se a autorização está ativa para uso
     */
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    /**
     * Data e hora de criação da autorização
     * Preenchida automaticamente quando a autorização é criada
     */
    @Column(name = "data_criacao", nullable = false)
>>>>>>> origin/main
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-autorizacoes")
    private Usuario usuario;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTipoBanco() { return tipoBanco; }
    public void setTipoBanco(String tipoBanco) { this.tipoBanco = tipoBanco; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public LocalDateTime getDataExpiracao() { return dataExpiracao; }
    public void setDataExpiracao(LocalDateTime dataExpiracao) { this.dataExpiracao = dataExpiracao; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

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
    public void setBanco(String banco) { this.tipoBanco = banco; } // Método de compatibilidade
    
    public boolean isTokenValido() {
        return ativo && dataExpiracao != null && dataExpiracao.isAfter(LocalDateTime.now());
    }
    
    public boolean precisaRenovacao() {
        if (dataExpiracao == null) return false;
        // Renovar se faltar menos de 1 hora para expirar
        return dataExpiracao.minusHours(1).isBefore(LocalDateTime.now());
    }

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    /**
<<<<<<< HEAD
=======
     * Verifica se o token de acesso ainda é válido
     * 
     * @return true se o token não expirou, false caso contrário
     */
    public boolean isTokenValido() {
        return dataExpiracao != null && 
               LocalDateTime.now().isBefore(dataExpiracao) && 
               ativo;
    }

    /**
>>>>>>> origin/main
     * Verifica se o token de acesso expirou
     * 
     * @return true se o token expirou, false caso contrário
     */
    public boolean isTokenExpirado() {
<<<<<<< HEAD
        return dataExpiracao != null && LocalDateTime.now().isAfter(dataExpiracao);
=======
        return dataExpiracao != null && 
               LocalDateTime.now().isAfter(dataExpiracao);
>>>>>>> origin/main
    }

    /**
     * Verifica se o token está próximo de expirar (dentro de 1 hora)
     * 
     * @return true se o token expira em breve, false caso contrário
     */
<<<<<<< HEAD
    public boolean isTokenExpirandoEmBreve() {
        if (dataExpiracao == null) return false;
        LocalDateTime umaHoraAntes = dataExpiracao.minusHours(1);
        return LocalDateTime.now().isAfter(umaHoraAntes);
    }
=======
    public boolean precisaRenovacao() {
        if (dataExpiracao == null) return false;
        LocalDateTime umaHoraAntes = dataExpiracao.minusHours(1);
        return LocalDateTime.now().isAfter(umaHoraAntes) && ativo;
    }


>>>>>>> origin/main
}
