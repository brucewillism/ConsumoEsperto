package com.consumoesperto.dto;

import java.math.BigDecimal;

public class OrcamentoDTO {
    private Long id;
    private Long categoriaId;
    private String categoriaNome;
    private BigDecimal valorLimite;
    private Integer mes;
    private Integer ano;
    private BigDecimal valorGasto;
    private BigDecimal percentualUso;
    private String status;
    private Boolean compartilhado;
    private Long grupoFamiliarId;
    private Integer membrosContabilizados;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public String getCategoriaNome() { return categoriaNome; }
    public void setCategoriaNome(String categoriaNome) { this.categoriaNome = categoriaNome; }

    public BigDecimal getValorLimite() { return valorLimite; }
    public void setValorLimite(BigDecimal valorLimite) { this.valorLimite = valorLimite; }

    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public BigDecimal getValorGasto() { return valorGasto; }
    public void setValorGasto(BigDecimal valorGasto) { this.valorGasto = valorGasto; }

    public BigDecimal getPercentualUso() { return percentualUso; }
    public void setPercentualUso(BigDecimal percentualUso) { this.percentualUso = percentualUso; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getCompartilhado() { return compartilhado; }
    public void setCompartilhado(Boolean compartilhado) { this.compartilhado = compartilhado; }

    public Long getGrupoFamiliarId() { return grupoFamiliarId; }
    public void setGrupoFamiliarId(Long grupoFamiliarId) { this.grupoFamiliarId = grupoFamiliarId; }

    public Integer getMembrosContabilizados() { return membrosContabilizados; }
    public void setMembrosContabilizados(Integer membrosContabilizados) { this.membrosContabilizados = membrosContabilizados; }
}
