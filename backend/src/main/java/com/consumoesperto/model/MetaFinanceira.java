package com.consumoesperto.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "metas_financeiras")
@NoArgsConstructor
@AllArgsConstructor
public class MetaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String descricao;

    @NotNull
    @Column(name = "valor_total", precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @NotNull
    @Column(name = "percentual_comprometimento", precision = 5, scale = 2)
    private BigDecimal percentualComprometimento;

    @NotNull
    @Column(name = "valor_poupado_mensal", precision = 19, scale = 2)
    private BigDecimal valorPoupadoMensal;

    @NotNull
    @Column(name = "prazo_meses", precision = 10, scale = 2)
    private BigDecimal prazoMeses;

    @Column(name = "renda_media_referencia", precision = 19, scale = 2)
    private BigDecimal rendaMediaReferencia;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    /** 1 = menor prioridade, 5 = maior (exibidas primeiro). */
    @NotNull
    @Column(name = "prioridade", nullable = false)
    private Integer prioridade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonBackReference("usuario-metas")
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (prioridade == null) {
            prioridade = 3;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public BigDecimal getPercentualComprometimento() {
        return percentualComprometimento;
    }

    public void setPercentualComprometimento(BigDecimal percentualComprometimento) {
        this.percentualComprometimento = percentualComprometimento;
    }

    public BigDecimal getValorPoupadoMensal() {
        return valorPoupadoMensal;
    }

    public void setValorPoupadoMensal(BigDecimal valorPoupadoMensal) {
        this.valorPoupadoMensal = valorPoupadoMensal;
    }

    public BigDecimal getPrazoMeses() {
        return prazoMeses;
    }

    public void setPrazoMeses(BigDecimal prazoMeses) {
        this.prazoMeses = prazoMeses;
    }

    public BigDecimal getRendaMediaReferencia() {
        return rendaMediaReferencia;
    }

    public void setRendaMediaReferencia(BigDecimal rendaMediaReferencia) {
        this.rendaMediaReferencia = rendaMediaReferencia;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Integer getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(Integer prioridade) {
        this.prioridade = prioridade;
    }
}
