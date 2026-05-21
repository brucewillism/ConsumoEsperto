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

/**
 * Carteira bancária do usuário (multicarteira): saldo mantido por movimentações confirmadas.
 */
@Entity
@Table(name = "contas_bancarias")
@NoArgsConstructor
@AllArgsConstructor
public class ContaBancaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoConta tipo;

    @NotNull
    @Column(name = "saldo_atual", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoAtual;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonBackReference("usuario-contas")
    private Usuario usuario;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;

    /** Conta preferencial quando a transação não informa carteira. */
    @Column(name = "padrao", nullable = false)
    private boolean padrao = false;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        dataCriacao = now;
        dataAtualizacao = now;
        if (saldoAtual == null) {
            saldoAtual = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }

    public enum TipoConta {
        CORRENTE,
        POUPANCA,
        DINHEIRO
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public TipoConta getTipo() { return tipo; }
    public void setTipo(TipoConta tipo) { this.tipo = tipo; }

    public BigDecimal getSaldoAtual() { return saldoAtual; }
    public void setSaldoAtual(BigDecimal saldoAtual) { this.saldoAtual = saldoAtual; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }

    public boolean isPadrao() { return padrao; }
    public void setPadrao(boolean padrao) { this.padrao = padrao; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
