package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.util.List;

public class ForecastFinanceiroDTO {
    private int diaAtual;
    private int diasNoMes;
    private BigDecimal rendaLiquida;
    private BigDecimal gastoAtual;
    private BigDecimal mediaDiaria;
    private BigDecimal gastoProjetado;
    private BigDecimal saldoProjetado;
    private BigDecimal probabilidadeVermelho;
    private String nivelRisco;
    private String mensagemIa;
    private List<String> maioresCategorias;

    public int getDiaAtual() { return diaAtual; }
    public void setDiaAtual(int diaAtual) { this.diaAtual = diaAtual; }

    public int getDiasNoMes() { return diasNoMes; }
    public void setDiasNoMes(int diasNoMes) { this.diasNoMes = diasNoMes; }

    public BigDecimal getRendaLiquida() { return rendaLiquida; }
    public void setRendaLiquida(BigDecimal rendaLiquida) { this.rendaLiquida = rendaLiquida; }

    public BigDecimal getGastoAtual() { return gastoAtual; }
    public void setGastoAtual(BigDecimal gastoAtual) { this.gastoAtual = gastoAtual; }

    public BigDecimal getMediaDiaria() { return mediaDiaria; }
    public void setMediaDiaria(BigDecimal mediaDiaria) { this.mediaDiaria = mediaDiaria; }

    public BigDecimal getGastoProjetado() { return gastoProjetado; }
    public void setGastoProjetado(BigDecimal gastoProjetado) { this.gastoProjetado = gastoProjetado; }

    public BigDecimal getSaldoProjetado() { return saldoProjetado; }
    public void setSaldoProjetado(BigDecimal saldoProjetado) { this.saldoProjetado = saldoProjetado; }

    public BigDecimal getProbabilidadeVermelho() { return probabilidadeVermelho; }
    public void setProbabilidadeVermelho(BigDecimal probabilidadeVermelho) { this.probabilidadeVermelho = probabilidadeVermelho; }

    public String getNivelRisco() { return nivelRisco; }
    public void setNivelRisco(String nivelRisco) { this.nivelRisco = nivelRisco; }

    public String getMensagemIa() { return mensagemIa; }
    public void setMensagemIa(String mensagemIa) { this.mensagemIa = mensagemIa; }

    public List<String> getMaioresCategorias() { return maioresCategorias; }
    public void setMaioresCategorias(List<String> maioresCategorias) { this.maioresCategorias = maioresCategorias; }
}
