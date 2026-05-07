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
}
