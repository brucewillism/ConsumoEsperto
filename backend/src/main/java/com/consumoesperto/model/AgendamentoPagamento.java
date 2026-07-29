package com.consumoesperto.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pagamento de boleto ou Pix agendado para execução na data de vencimento.
 * Registro interno — não liquida em API bancária real.
 */
@Entity
@Table(name = "agendamentos_pagamentos")
public class AgendamentoPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_debito_id", nullable = false)
    private ContaBancaria contaDebito;

    @Column(name = "beneficiario", nullable = false, length = 200)
    private String beneficiario;

    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    /** Linha digitável do boleto ou payload Pix copia e cola (higienizado, sem quebras). */
    @Column(name = "codigo_barras_ou_pix", columnDefinition = "TEXT")
    private String codigoBarrasOuPix;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StatusAgendamento status = StatusAgendamento.AGENDADO;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_processamento")
    private LocalDateTime dataProcessamento;

    @Column(name = "mensagem_erro", length = 500)
    private String mensagemErro;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorrencia", length = 16)
    private RecorrenciaAgendamento recorrencia = RecorrenciaAgendamento.UNICA;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "proxima_execucao")
    private LocalDate proximaExecucao;

    @Column(name = "ultima_execucao")
    private LocalDate ultimaExecucao;

    @Column(name = "dia_vencimento_mensal")
    private Integer diaVencimentoMensal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_credito_id")
    private CartaoCredito cartaoCredito;

    @Column(name = "falhas_consecutivas", nullable = false)
    private int falhasConsecutivas = 0;

    @Column(name = "ultima_chave_execucao", length = 64)
    private String ultimaChaveExecucao;

    @PrePersist
    protected void onCreate() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (status == null) {
            status = StatusAgendamento.AGENDADO;
        }
    }

    public enum StatusAgendamento {
        AGENDADO, PAUSADO, PAGO, FALHOU, CANCELADO
    }

    public enum RecorrenciaAgendamento {
        UNICA, DIARIA, SEMANAL, QUINZENAL, MENSAL, BIMESTRAL, TRIMESTRAL, SEMESTRAL, ANUAL
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public ContaBancaria getContaDebito() { return contaDebito; }
    public void setContaDebito(ContaBancaria contaDebito) { this.contaDebito = contaDebito; }

    public String getBeneficiario() { return beneficiario; }
    public void setBeneficiario(String beneficiario) { this.beneficiario = beneficiario; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }

    public String getCodigoBarrasOuPix() { return codigoBarrasOuPix; }
    public void setCodigoBarrasOuPix(String codigoBarrasOuPix) { this.codigoBarrasOuPix = codigoBarrasOuPix; }

    public StatusAgendamento getStatus() { return status; }
    public void setStatus(StatusAgendamento status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataProcessamento() { return dataProcessamento; }
    public void setDataProcessamento(LocalDateTime dataProcessamento) { this.dataProcessamento = dataProcessamento; }

    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String mensagemErro) { this.mensagemErro = mensagemErro; }

    public RecorrenciaAgendamento getRecorrencia() { return recorrencia; }
    public void setRecorrencia(RecorrenciaAgendamento recorrencia) { this.recorrencia = recorrencia; }
    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }
    public LocalDate getProximaExecucao() { return proximaExecucao; }
    public void setProximaExecucao(LocalDate proximaExecucao) { this.proximaExecucao = proximaExecucao; }
    public LocalDate getUltimaExecucao() { return ultimaExecucao; }
    public void setUltimaExecucao(LocalDate ultimaExecucao) { this.ultimaExecucao = ultimaExecucao; }
    public Integer getDiaVencimentoMensal() { return diaVencimentoMensal; }
    public void setDiaVencimentoMensal(Integer diaVencimentoMensal) { this.diaVencimentoMensal = diaVencimentoMensal; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public CartaoCredito getCartaoCredito() { return cartaoCredito; }
    public void setCartaoCredito(CartaoCredito cartaoCredito) { this.cartaoCredito = cartaoCredito; }
    public int getFalhasConsecutivas() { return falhasConsecutivas; }
    public void setFalhasConsecutivas(int falhasConsecutivas) { this.falhasConsecutivas = falhasConsecutivas; }
    public String getUltimaChaveExecucao() { return ultimaChaveExecucao; }
    public void setUltimaChaveExecucao(String ultimaChaveExecucao) { this.ultimaChaveExecucao = ultimaChaveExecucao; }
}
