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
        AGENDADO, PAGO, FALHOU, CANCELADO
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
}
