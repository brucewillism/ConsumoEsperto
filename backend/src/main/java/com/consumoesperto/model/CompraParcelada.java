package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "compras_parceladas")
@NoArgsConstructor
@AllArgsConstructor
public class CompraParcelada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    private String descricao;

    @NotNull
    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @NotNull
    @Column(name = "valor_parcela")
    private BigDecimal valorParcela;

    @NotNull
    @Column(name = "numero_parcelas")
    private Integer numeroParcelas;

    @NotNull
    @Column(name = "parcela_atual")
    private Integer parcelaAtual = 1;

    @Column(name = "data_compra")
    private LocalDateTime dataCompra;

    @Column(name = "data_primeira_parcela")
    private LocalDateTime dataPrimeiraParcela;

    @Column(name = "data_ultima_parcela")
    private LocalDateTime dataUltimaParcela;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_compra")
    private StatusCompra statusCompra = StatusCompra.ATIVA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_credito_id")
    @JsonBackReference("cartao-compras")
    private CartaoCredito cartaoCredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    @JsonBackReference("categoria-compras")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @JsonBackReference("usuario-compras")
    private Usuario usuario;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getValorParcela() { return valorParcela; }
    public void setValorParcela(BigDecimal valorParcela) { this.valorParcela = valorParcela; }

    public Integer getNumeroParcelas() { return numeroParcelas; }
    public void setNumeroParcelas(Integer numeroParcelas) { this.numeroParcelas = numeroParcelas; }

    public Integer getParcelaAtual() { return parcelaAtual; }
    public void setParcelaAtual(Integer parcelaAtual) { this.parcelaAtual = parcelaAtual; }

    public LocalDateTime getDataCompra() { return dataCompra; }
    public void setDataCompra(LocalDateTime dataCompra) { this.dataCompra = dataCompra; }

    public LocalDateTime getDataPrimeiraParcela() { return dataPrimeiraParcela; }
    public void setDataPrimeiraParcela(LocalDateTime dataPrimeiraParcela) { this.dataPrimeiraParcela = dataPrimeiraParcela; }

    public LocalDateTime getDataUltimaParcela() { return dataUltimaParcela; }
    public void setDataUltimaParcela(LocalDateTime dataUltimaParcela) { this.dataUltimaParcela = dataUltimaParcela; }

    public StatusCompra getStatusCompra() { return statusCompra; }
    public void setStatusCompra(StatusCompra statusCompra) { this.statusCompra = statusCompra; }

    public CartaoCredito getCartaoCredito() { return cartaoCredito; }
    public void setCartaoCredito(CartaoCredito cartaoCredito) { this.cartaoCredito = cartaoCredito; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
        if (dataCompra == null) {
            dataCompra = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public enum StatusCompra {
        ATIVA,
        FINALIZADA,
        CANCELADA
    }
}
