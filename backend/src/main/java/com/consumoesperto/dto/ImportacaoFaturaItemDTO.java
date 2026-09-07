package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ImportacaoFaturaItemDTO {
    private LocalDate data;
    private String descricao;
    private BigDecimal valor;
    private Integer parcelaAtual;
    private Integer totalParcelas;
    private boolean novo;
    private boolean selecionado;

    private Integer sourceLine;
    private String merchant;
    private String currency;
    private String tipoLinha;
    private String statusPreview;
    private String accountHint;
    private String cardHint;
    private String externalId;
    private String fingerprint;
    private String rawReference;
    private Long matchedTransacaoId;
    private Long categoriaId;
    private Long contaBancariaId;
    private Long cartaoCreditoId;
    private Long faturaId;
    private String faturaPrevistaLabel;
    private String invalidReason;

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public Integer getParcelaAtual() { return parcelaAtual; }
    public void setParcelaAtual(Integer parcelaAtual) { this.parcelaAtual = parcelaAtual; }

    public Integer getTotalParcelas() { return totalParcelas; }
    public void setTotalParcelas(Integer totalParcelas) { this.totalParcelas = totalParcelas; }

    public boolean isNovo() { return novo; }
    public void setNovo(boolean novo) { this.novo = novo; }

    public boolean isSelecionado() { return selecionado; }
    public void setSelecionado(boolean selecionado) { this.selecionado = selecionado; }

    public Integer getSourceLine() { return sourceLine; }
    public void setSourceLine(Integer sourceLine) { this.sourceLine = sourceLine; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTipoLinha() { return tipoLinha; }
    public void setTipoLinha(String tipoLinha) { this.tipoLinha = tipoLinha; }

    public String getStatusPreview() { return statusPreview; }
    public void setStatusPreview(String statusPreview) { this.statusPreview = statusPreview; }

    public String getAccountHint() { return accountHint; }
    public void setAccountHint(String accountHint) { this.accountHint = accountHint; }

    public String getCardHint() { return cardHint; }
    public void setCardHint(String cardHint) { this.cardHint = cardHint; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getRawReference() { return rawReference; }
    public void setRawReference(String rawReference) { this.rawReference = rawReference; }

    public Long getMatchedTransacaoId() { return matchedTransacaoId; }
    public void setMatchedTransacaoId(Long matchedTransacaoId) { this.matchedTransacaoId = matchedTransacaoId; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public Long getContaBancariaId() { return contaBancariaId; }
    public void setContaBancariaId(Long contaBancariaId) { this.contaBancariaId = contaBancariaId; }

    public Long getCartaoCreditoId() { return cartaoCreditoId; }
    public void setCartaoCreditoId(Long cartaoCreditoId) { this.cartaoCreditoId = cartaoCreditoId; }

    public Long getFaturaId() { return faturaId; }
    public void setFaturaId(Long faturaId) { this.faturaId = faturaId; }

    public String getFaturaPrevistaLabel() { return faturaPrevistaLabel; }
    public void setFaturaPrevistaLabel(String faturaPrevistaLabel) { this.faturaPrevistaLabel = faturaPrevistaLabel; }

    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }
}
