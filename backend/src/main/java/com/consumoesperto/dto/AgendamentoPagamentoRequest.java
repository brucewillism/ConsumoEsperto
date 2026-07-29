package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AgendamentoPagamentoRequest {
    private Long contaDebitoId;
    private String beneficiario;
    private BigDecimal valor;
    private LocalDate dataVencimento;
    private String codigoBarrasOuPix;

    private Long categoriaId;
    private Long cartaoCreditoId;
    private String recorrencia;
    private LocalDate dataFim;
    private Integer diaVencimentoMensal;

    public Long getContaDebitoId() { return contaDebitoId; }
    public void setContaDebitoId(Long contaDebitoId) { this.contaDebitoId = contaDebitoId; }
    public String getBeneficiario() { return beneficiario; }
    public void setBeneficiario(String beneficiario) { this.beneficiario = beneficiario; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }
    public String getCodigoBarrasOuPix() { return codigoBarrasOuPix; }
    public void setCodigoBarrasOuPix(String codigoBarrasOuPix) { this.codigoBarrasOuPix = codigoBarrasOuPix; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public Long getCartaoCreditoId() { return cartaoCreditoId; }
    public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }
    public String getRecorrencia() { return recorrencia; }
    public void setRecorrencia(String recorrencia) { this.recorrencia = recorrencia; }
    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }
    public Integer getDiaVencimentoMensal() { return diaVencimentoMensal; }
    public void setDiaVencimentoMensal(Integer diaVencimentoMensal) { this.diaVencimentoMensal = diaVencimentoMensal; }
}
