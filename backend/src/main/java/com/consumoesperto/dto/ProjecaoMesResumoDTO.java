package com.consumoesperto.dto;

import java.math.BigDecimal;

public class ProjecaoMesResumoDTO {
    private String competencia;
    private String rotuloMes;
    private BigDecimal patrimonioInicial;
    private BigDecimal patrimonioLiquido;
    private BigDecimal receitasPrevistas;
    private BigDecimal receitasFiscaisPrevistas;
    private BigDecimal despesasPrevistas;
    private BigDecimal saldoProjetadoFimMes;

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getRotuloMes() { return rotuloMes; }
    public void setRotuloMes(String rotuloMes) { this.rotuloMes = rotuloMes; }

    public BigDecimal getPatrimonioInicial() { return patrimonioInicial; }
    public void setPatrimonioInicial(BigDecimal patrimonioInicial) { this.patrimonioInicial = patrimonioInicial; }

    public BigDecimal getPatrimonioLiquido() { return patrimonioLiquido; }
    public void setPatrimonioLiquido(BigDecimal patrimonioLiquido) { this.patrimonioLiquido = patrimonioLiquido; }

    public BigDecimal getReceitasPrevistas() { return receitasPrevistas; }
    public void setReceitasPrevistas(BigDecimal receitasPrevistas) { this.receitasPrevistas = receitasPrevistas; }

    public BigDecimal getReceitasFiscaisPrevistas() { return receitasFiscaisPrevistas; }
    public void setReceitasFiscaisPrevistas(BigDecimal receitasFiscaisPrevistas) {
        this.receitasFiscaisPrevistas = receitasFiscaisPrevistas;
    }

    public BigDecimal getDespesasPrevistas() { return despesasPrevistas; }
    public void setDespesasPrevistas(BigDecimal despesasPrevistas) { this.despesasPrevistas = despesasPrevistas; }

    public BigDecimal getSaldoProjetadoFimMes() { return saldoProjetadoFimMes; }
    public void setSaldoProjetadoFimMes(BigDecimal saldoProjetadoFimMes) { this.saldoProjetadoFimMes = saldoProjetadoFimMes; }
}
