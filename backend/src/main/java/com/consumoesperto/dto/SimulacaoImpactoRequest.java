package com.consumoesperto.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

public class SimulacaoImpactoRequest {
    @NotBlank
    private String descricao;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal valorMensalImpacto;

    private Integer mesesImpacto;
    private String metaDescricao;
    private String icone;

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getValorMensalImpacto() { return valorMensalImpacto; }
    public void setValorMensalImpacto(BigDecimal valorMensalImpacto) { this.valorMensalImpacto = valorMensalImpacto; }

    public Integer getMesesImpacto() { return mesesImpacto; }
    public void setMesesImpacto(Integer mesesImpacto) { this.mesesImpacto = mesesImpacto; }

    public String getMetaDescricao() { return metaDescricao; }
    public void setMetaDescricao(String metaDescricao) { this.metaDescricao = metaDescricao; }

    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }
}
