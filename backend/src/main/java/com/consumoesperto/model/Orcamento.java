package com.consumoesperto.model;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "orcamentos",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_orcamento_usuario_categoria_mes_ano",
        columnNames = {"usuario_id", "categoria_id", "mes", "ano"}
    )
)
public class Orcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_familiar_id")
    private GrupoFamiliar grupoFamiliar;

    @Column(name = "compartilhado", nullable = false)
    private boolean compartilhado;

    @NotNull
    @Column(name = "valor_limite", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLimite;

    @Column(name = "mes", nullable = false)
    private Integer mes;

    @Column(name = "ano", nullable = false)
    private Integer ano;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = dataCriacao;
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public GrupoFamiliar getGrupoFamiliar() { return grupoFamiliar; }
    public void setGrupoFamiliar(GrupoFamiliar grupoFamiliar) { this.grupoFamiliar = grupoFamiliar; }

    public boolean isCompartilhado() { return compartilhado; }
    public void setCompartilhado(boolean compartilhado) { this.compartilhado = compartilhado; }

    public BigDecimal getValorLimite() { return valorLimite; }
    public void setValorLimite(BigDecimal valorLimite) { this.valorLimite = valorLimite; }

    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
