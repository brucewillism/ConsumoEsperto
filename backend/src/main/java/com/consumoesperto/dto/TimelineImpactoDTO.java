package com.consumoesperto.dto;

public class TimelineImpactoDTO {
    private String titulo;
    private String icone;
    private Integer mesesOriginais;
    private Integer mesesProjetados;
    private Integer deslocamentoMeses;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }

    public Integer getMesesOriginais() { return mesesOriginais; }
    public void setMesesOriginais(Integer mesesOriginais) { this.mesesOriginais = mesesOriginais; }

    public Integer getMesesProjetados() { return mesesProjetados; }
    public void setMesesProjetados(Integer mesesProjetados) { this.mesesProjetados = mesesProjetados; }

    public Integer getDeslocamentoMeses() { return deslocamentoMeses; }
    public void setDeslocamentoMeses(Integer deslocamentoMeses) { this.deslocamentoMeses = deslocamentoMeses; }
}
