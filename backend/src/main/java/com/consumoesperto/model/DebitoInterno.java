package com.consumoesperto.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Débito interno do racha-contas (split bills) entre membros de um grupo familiar.
 *
 * <p>Representa um direito a receber do {@code credor} (quem pagou) sobre o {@code devedor}
 * (quem ficou devendo a sua fatia). Não movimenta saldo bancário de ninguém automaticamente —
 * é apenas o livro-razão interno do grupo. A liquidação é manual (Pix por fora) e marcada aqui.
 */
@Entity
@Table(name = "debitos_internos")
public class DebitoInterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_familiar_id", nullable = false)
    private GrupoFamiliar grupoFamiliar;

    /** Quem pagou e tem o direito a receber. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credor_usuario_id", nullable = false)
    private Usuario credor;

    /** Quem deve a sua fatia. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devedor_usuario_id", nullable = false)
    private Usuario devedor;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "descricao", length = 200)
    private String descricao;

    @Column(name = "liquidado", nullable = false)
    private boolean liquidado = false;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_liquidacao")
    private LocalDateTime dataLiquidacao;

    @PrePersist
    protected void onCreate() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public GrupoFamiliar getGrupoFamiliar() { return grupoFamiliar; }
    public void setGrupoFamiliar(GrupoFamiliar grupoFamiliar) { this.grupoFamiliar = grupoFamiliar; }

    public Usuario getCredor() { return credor; }
    public void setCredor(Usuario credor) { this.credor = credor; }

    public Usuario getDevedor() { return devedor; }
    public void setDevedor(Usuario devedor) { this.devedor = devedor; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public boolean isLiquidado() { return liquidado; }
    public void setLiquidado(boolean liquidado) { this.liquidado = liquidado; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataLiquidacao() { return dataLiquidacao; }
    public void setDataLiquidacao(LocalDateTime dataLiquidacao) { this.dataLiquidacao = dataLiquidacao; }
}
