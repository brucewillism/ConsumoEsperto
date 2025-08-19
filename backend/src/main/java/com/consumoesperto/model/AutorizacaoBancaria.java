package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Entidade que representa a autorização OAuth2 de um usuário com um banco
 * 
 * Esta classe gerencia os tokens de acesso OAuth2 que permitem ao sistema
 * consultar dados bancários em nome do usuário, incluindo cartões, saldos
 * e faturas através das APIs dos bancos.
 * 
 * Funcionalidades principais:
 * - Armazenamento seguro de tokens OAuth2
 * - Controle de expiração e renovação automática
 * - Histórico de autorizações por banco
 * - Rastreamento de permissões concedidas
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "autorizacoes_bancarias") // Nome da tabela no banco de dados
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class AutorizacaoBancaria {

    /**
     * Identificador único da autorização bancária (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private LocalDateTime dataCriacao;

    /**
     * Data e hora da última atualização da autorização
     * Atualizada automaticamente quando qualquer campo é modificado
     */
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define as datas de criação e atualização quando uma nova autorização é criada
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Método executado automaticamente antes de atualizar a entidade
     * Atualiza a data de modificação quando a autorização é alterada
     */
    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    /**
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
     * Verifica se o token de acesso expirou
     * 
     * @return true se o token expirou, false caso contrário
     */
    public boolean isTokenExpirado() {
        return dataExpiracao != null && 
               LocalDateTime.now().isAfter(dataExpiracao);
    }

    /**
     * Verifica se o token precisa ser renovado
     * 
     * Considera que um token precisa ser renovado quando está próximo
     * da expiração (dentro de 1 hora) para evitar interrupções
     * 
     * @return true se o token precisa ser renovado, false caso contrário
     */
    public boolean precisaRenovacao() {
        if (dataExpiracao == null) return false;
        LocalDateTime umaHoraAntes = dataExpiracao.minusHours(1);
        return LocalDateTime.now().isAfter(umaHoraAntes) && ativo;
    }


}
