package com.consumoesperto.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "importacoes_fatura_cartao")
public class ImportacaoFaturaCartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_credito_id")
    private CartaoCredito cartaoCredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id")
    private Fatura fatura;

    @Column(name = "banco_cartao", length = 120)
    private String bancoCartao;

    @Column(name = "data_vencimento")
    private LocalDateTime dataVencimento;

    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    @Column(name = "valor_total", precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "pagamento_minimo", precision = 19, scale = 2)
    private BigDecimal pagamentoMinimo;

    @Lob
    @Column(name = "itens_json", nullable = false)
    private String itensJson;

    @Lob
    @Column(name = "auditoria_json")
    private String auditoriaJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "novos_detectados", nullable = false)
    private int novosDetectados;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_confirmacao")
    private LocalDateTime dataConfirmacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        if (status == null) {
            status = Status.PENDENTE;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public CartaoCredito getCartaoCredito() { return cartaoCredito; }
    public void setCartaoCredito(CartaoCredito cartaoCredito) { this.cartaoCredito = cartaoCredito; }

    public Fatura getFatura() { return fatura; }
    public void setFatura(Fatura fatura) { this.fatura = fatura; }

    public String getBancoCartao() { return bancoCartao; }
    public void setBancoCartao(String bancoCartao) { this.bancoCartao = bancoCartao; }

    public LocalDateTime getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDateTime dataVencimento) { this.dataVencimento = dataVencimento; }

    public LocalDateTime getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(LocalDateTime dataFechamento) { this.dataFechamento = dataFechamento; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getPagamentoMinimo() { return pagamentoMinimo; }
    public void setPagamentoMinimo(BigDecimal pagamentoMinimo) { this.pagamentoMinimo = pagamentoMinimo; }

    public String getItensJson() { return itensJson; }
    public void setItensJson(String itensJson) { this.itensJson = itensJson; }

    public String getAuditoriaJson() { return auditoriaJson; }
    public void setAuditoriaJson(String auditoriaJson) { this.auditoriaJson = auditoriaJson; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getNovosDetectados() { return novosDetectados; }
    public void setNovosDetectados(int novosDetectados) { this.novosDetectados = novosDetectados; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataConfirmacao() { return dataConfirmacao; }
    public void setDataConfirmacao(LocalDateTime dataConfirmacao) { this.dataConfirmacao = dataConfirmacao; }

    public enum Status {
        PENDENTE,
        CONFIRMADA,
        CANCELADA
    }
}
