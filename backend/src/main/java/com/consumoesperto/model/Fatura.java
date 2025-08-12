package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade que representa uma fatura de cartão de crédito
 * 
 * As faturas controlam o ciclo de cobrança do cartão de crédito,
 * incluindo valores, datas de vencimento, pagamentos e status.
 * Permite acompanhar o controle financeiro mensal do cartão.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "faturas") // Nome da tabela no banco de dados
@Data // Lombok: gera getters, setters, toString, equals e hashCode
@NoArgsConstructor // Lombok: gera construtor sem argumentos
@AllArgsConstructor // Lombok: gera construtor com todos os argumentos
public class Fatura {

    /**
     * Identificador único da fatura (chave primária)
     * Gerado automaticamente pelo banco de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Valor total da fatura (gastos do período)
     * Deve ser informado e usar BigDecimal para precisão decimal
     */
    @NotNull(message = "Valor da fatura é obrigatório")
    @Column(name = "valor_fatura")
    private BigDecimal valorFatura;

    /**
     * Valor já pago da fatura
     * Inicializa com zero e é atualizado conforme pagamentos
     * Usa BigDecimal para precisão decimal
     */
    @NotNull(message = "Valor pago é obrigatório")
    @Column(name = "valor_pago")
    private BigDecimal valorPago = BigDecimal.ZERO;

    /**
     * Data de vencimento da fatura
     * Data limite para pagamento sem juros
     */
    @Column(name = "data_vencimento")
    private LocalDateTime dataVencimento;

    /**
     * Data de fechamento da fatura
     * Data em que o período de cobrança é encerrado
     */
    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    /**
     * Data em que a fatura foi paga
     * Preenchida quando o pagamento é registrado
     */
    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    /**
     * Status atual da fatura
     * Usa enum para garantir valores válidos
     * Inicializa como ABERTA
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_fatura")
    private StatusFatura statusFatura = StatusFatura.ABERTA;

    /**
     * Número identificador da fatura
     * Pode ser o número da fatura do banco ou um identificador interno
     */
    @Column(name = "numero_fatura")
    private String numeroFatura;

    /**
     * Mês da fatura (1-12)
     */
    @Column(name = "mes")
    private Integer mes;

    /**
     * Ano da fatura
     */
    @Column(name = "ano")
    private Integer ano;

    /**
     * Valor total da fatura
     */
    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    /**
     * Status da fatura como string
     */
    @Column(name = "status")
    private String status;

    /**
     * Cartão de crédito ao qual a fatura pertence
     * Relacionamento muitos-para-um: várias faturas podem pertencer a um cartão
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_credito_id")
    private CartaoCredito cartaoCredito;

    /**
     * Data e hora de criação da fatura no sistema
     * Preenchida automaticamente quando a fatura é criada
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    /**
     * Data e hora da última atualização da fatura
     * Atualizada automaticamente quando qualquer campo é modificado
     */
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define as datas de criação e atualização quando uma nova fatura é criada
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Método executado automaticamente antes de atualizar a entidade
     * Atualiza a data de modificação quando a fatura é alterada
     */
    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    /**
     * Enum que define os status possíveis de uma fatura
     * Controla o ciclo de vida da fatura desde a abertura até o pagamento
     */
    public enum StatusFatura {
        ABERTA,   // Fatura em período de cobrança
        FECHADA,  // Fatura fechada, aguardando pagamento
        PAGA,     // Fatura totalmente paga
        VENCIDA   // Fatura com prazo de vencimento expirado
    }
}
