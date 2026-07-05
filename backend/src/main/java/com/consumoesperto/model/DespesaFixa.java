package com.consumoesperto.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "despesas_fixas")
public class DespesaFixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "descricao", nullable = false, length = 200)
    private String descricao;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @Column(name = "categoria", length = 120)
    private String categoria;

    /** Quando true, o job diário lança a despesa na conta no dia do vencimento (débito real, não só projeção). */
    @Column(name = "debito_automatico", nullable = false)
    private boolean debitoAutomatico = false;

    /** Conta debitada pelo débito automático; null = conta padrão do usuário no momento do débito. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_bancaria_id")
    private ContaBancaria contaBancaria;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        dataCriacao = now;
        dataAtualizacao = now;
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public Integer getDiaVencimento() { return diaVencimento; }
    public void setDiaVencimento(Integer diaVencimento) { this.diaVencimento = diaVencimento; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public boolean isDebitoAutomatico() { return debitoAutomatico; }
    public void setDebitoAutomatico(boolean debitoAutomatico) { this.debitoAutomatico = debitoAutomatico; }

    public ContaBancaria getContaBancaria() { return contaBancaria; }
    public void setContaBancaria(ContaBancaria contaBancaria) { this.contaBancaria = contaBancaria; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
