package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

/**
 * Entidade que representa uma parcela de uma compra parcelada
 * 
 * Esta classe gerencia informações sobre cada parcela individual,
 * incluindo valores, datas de vencimento e status de pagamento.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Entity
@Table(name = "parcelas")
@NoArgsConstructor
@AllArgsConstructor
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Número da parcela é obrigatório")
    @Column(name = "numero_parcela")
    private Integer numeroParcela;

    @NotNull(message = "Valor da parcela é obrigatório")
    @Column(name = "valor_parcela")
    private BigDecimal valorParcela;

    @NotNull(message = "Data de vencimento é obrigatória")
    @Column(name = "data_vencimento")
    private LocalDateTime dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "valor_pago")
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusParcela status = StatusParcela.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_parcelada_id")
    @JsonBackReference("compra-parcelas")
    private CompraParcelada compraParcelada;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getNumeroParcela() { return numeroParcela; }
    public void setNumeroParcela(Integer numeroParcela) { this.numeroParcela = numeroParcela; }

    public BigDecimal getValorParcela() { return valorParcela; }
    public void setValorParcela(BigDecimal valorParcela) { this.valorParcela = valorParcela; }

    public LocalDateTime getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDateTime dataVencimento) { this.dataVencimento = dataVencimento; }

    public LocalDateTime getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDateTime dataPagamento) { this.dataPagamento = dataPagamento; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public StatusParcela getStatus() { return status; }
    public void setStatus(StatusParcela status) { this.status = status; }

    public CompraParcelada getCompraParcelada() { return compraParcelada; }
    public void setCompraParcelada(CompraParcelada compraParcelada) { this.compraParcelada = compraParcelada; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    // Método de compatibilidade para código existente
    public void setValor(BigDecimal valor) { this.valorParcela = valor; }

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }

    /**
     * Enum que define os status possíveis de uma parcela
     */
    public enum StatusParcela {
        PENDENTE,   // Parcela aguardando pagamento
        PAGA,       // Parcela paga integralmente
        PARCIAL,    // Parcela com pagamento parcial
        VENCIDA,    // Parcela com vencimento ultrapassado
        CANCELADA   // Parcela cancelada
    }
}
