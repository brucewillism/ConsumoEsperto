package com.consumoesperto.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

public class OrcamentoRequest {
    @NotNull
    private Long categoriaId;

    @NotNull
    @DecimalMin(value = "0.01", message = "O limite precisa ser maior que zero")
    private BigDecimal valorLimite;

    @Min(1)
    @Max(12)
    private Integer mes;

    @Min(2000)
    @Max(2100)
    private Integer ano;

    private Boolean compartilhado;

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public BigDecimal getValorLimite() { return valorLimite; }
    public void setValorLimite(BigDecimal valorLimite) { this.valorLimite = valorLimite; }

    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public Boolean getCompartilhado() { return compartilhado; }
    public void setCompartilhado(Boolean compartilhado) { this.compartilhado = compartilhado; }
}
