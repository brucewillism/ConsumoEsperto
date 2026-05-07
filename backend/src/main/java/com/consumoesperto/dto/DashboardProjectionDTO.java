package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardProjectionDTO {
    private List<String> labels;
    private List<BigDecimal> real;
    private List<BigDecimal> projetado;
    private List<BigDecimal> simulado;
    private List<TimelineImpactoDTO> timelineImpacto;
    private List<SimulacaoImpactoDTO> simulacoesAtivas;

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public List<BigDecimal> getReal() { return real; }
    public void setReal(List<BigDecimal> real) { this.real = real; }

    public List<BigDecimal> getProjetado() { return projetado; }
    public void setProjetado(List<BigDecimal> projetado) { this.projetado = projetado; }

    public List<BigDecimal> getSimulado() { return simulado; }
    public void setSimulado(List<BigDecimal> simulado) { this.simulado = simulado; }

    public List<TimelineImpactoDTO> getTimelineImpacto() { return timelineImpacto; }
    public void setTimelineImpacto(List<TimelineImpactoDTO> timelineImpacto) { this.timelineImpacto = timelineImpacto; }

    public List<SimulacaoImpactoDTO> getSimulacoesAtivas() { return simulacoesAtivas; }
    public void setSimulacoesAtivas(List<SimulacaoImpactoDTO> simulacoesAtivas) { this.simulacoesAtivas = simulacoesAtivas; }
}
