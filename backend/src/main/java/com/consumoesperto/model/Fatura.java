package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

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
     * Número da fatura (ex: "2024-001", "2024-002")
     * Deve ser único por cartão e período
     */
    @NotNull(message = "Número da fatura é obrigatório")
    @Column(name = "numero_fatura", unique = true)
    private String numeroFatura;

    /**
     * Valor total da fatura
     * Deve ser maior que zero e usar BigDecimal para precisão decimal
     */
    @NotNull(message = "Valor total da fatura é obrigatório")
    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    /**
     * Valor da fatura (campo obrigatório no banco)
     * Usado para compatibilidade com a estrutura do banco
     */
    @NotNull(message = "Valor da fatura é obrigatório")
    @Column(name = "valor_fatura")
    private BigDecimal valorFatura;

    /**
     * Valor mínimo para pagamento
     * Deve ser maior que zero e menor ou igual ao valor total
     */
    @NotNull(message = "Valor mínimo para pagamento é obrigatório")
    @Column(name = "valor_minimo")
    private BigDecimal valorMinimo;

    /**
     * Data de vencimento da fatura
     * Deve ser uma data futura quando a fatura for criada
     */
    @NotNull(message = "Data de vencimento é obrigatória")
    @Column(name = "data_vencimento")
    private LocalDateTime dataVencimento;

    /**
     * Data de fechamento da fatura
     * Data em que o período de cobrança foi fechado
     */
    @NotNull(message = "Data de fechamento é obrigatória")
    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    /**
     * Status atual da fatura
     * Usa enum para garantir valores válidos
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusFatura status;

    /**
     * Indica se a fatura foi paga
     * Controla se o pagamento foi realizado
     */
    @Column(name = "paga")
    private Boolean paga = false;

    /**
     * Data em que a fatura foi paga
     * Preenchida automaticamente quando o pagamento é registrado
     */
    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    /**
     * Valor efetivamente pago
     * Pode ser diferente do valor total (pagamento parcial)
     */
    @Column(name = "valor_pago")
    private BigDecimal valorPago;

    /**
     * Cartão de crédito ao qual a fatura pertence
     * Relacionamento muitos-para-um: várias faturas podem pertencer a um cartão
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_credito_id")
    @JsonBackReference("cartao-faturas")
    private CartaoCredito cartaoCredito;

    /**
     * Usuário proprietário da fatura
     * Relacionamento muitos-para-um: várias faturas podem pertencer a um usuário
     * Carregamento lazy para melhor performance
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-faturas")
    private Usuario usuario;

    /**
     * Data e hora de criação da fatura no sistema
     * Preenchida automaticamente quando a fatura é criada
     */
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroFatura() { return numeroFatura; }
    public void setNumeroFatura(String numeroFatura) { this.numeroFatura = numeroFatura; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getValorMinimo() { return valorMinimo; }
    public void setValorMinimo(BigDecimal valorMinimo) { this.valorMinimo = valorMinimo; }

    public LocalDateTime getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDateTime dataVencimento) { this.dataVencimento = dataVencimento; }

    public LocalDateTime getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(LocalDateTime dataFechamento) { this.dataFechamento = dataFechamento; }

    public StatusFatura getStatus() { return status; }
    public void setStatus(StatusFatura status) { this.status = status; }

    public Boolean getPaga() { return paga; }
    public void setPaga(Boolean paga) { this.paga = paga; }

    public LocalDateTime getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDateTime dataPagamento) { this.dataPagamento = dataPagamento; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public CartaoCredito getCartaoCredito() { return cartaoCredito; }
    public void setCartaoCredito(CartaoCredito cartaoCredito) { this.cartaoCredito = cartaoCredito; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    // Métodos de compatibilidade para código existente
    public BigDecimal getValorFatura() { return valorFatura; }
    public void setValorFatura(BigDecimal valorFatura) { this.valorFatura = valorFatura; }
    
    public StatusFatura getStatusFatura() { return status; }
    public void setStatusFatura(StatusFatura statusFatura) { this.status = statusFatura; }
    
    public LocalDateTime getDataAtualizacao() { return dataCriacao; } // Usa dataCriacao como fallback
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { /* Campo removido */ }
    
    public Integer getMes() { 
        if (dataFechamento != null) {
            return dataFechamento.getMonthValue();
        }
        return null;
    }
    public void setMes(Integer mes) { 
        if (dataFechamento != null && mes != null) {
            this.dataFechamento = dataFechamento.withMonth(mes);
        }
    }
    
    public Integer getAno() { 
        if (dataFechamento != null) {
            return dataFechamento.getYear();
        }
        return null;
    }
    public void setAno(Integer ano) { 
        if (dataFechamento != null && ano != null) {
            this.dataFechamento = dataFechamento.withYear(ano);
        }
    }

    /**
     * Método executado automaticamente antes de persistir a entidade
     * Define a data de criação quando uma nova fatura é criada
     */
    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }

    /**
     * Enum que define os status possíveis de uma fatura
     */
    public enum StatusFatura {
        ABERTA,     // Fatura em aberto, aguardando pagamento
        VENCIDA,    // Fatura com vencimento ultrapassado
        PAGA,       // Fatura paga integralmente
        PARCIAL,    // Fatura com pagamento parcial
        CANCELADA;  // Fatura cancelada
        
        // Método de compatibilidade para converter String para StatusFatura
        public static StatusFatura fromString(String status) {
            if (status == null) return null;
            try {
                return StatusFatura.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Retorna ABERTA como padrão se não conseguir converter
                return ABERTA;
            }
        }
    }
}
