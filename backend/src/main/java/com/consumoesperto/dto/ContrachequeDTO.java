package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ContrachequeDTO {
    private Long id;
    private String empresa;
    private Integer mes;
    private Integer ano;
    private BigDecimal salarioBruto;
    private BigDecimal salarioLiquido;
    private BigDecimal totalDescontos;
    private List<DescontoFixoDTO> descontos;
    private List<String> insights;
    private String status;
    private LocalDateTime dataCriacao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public Integer getMes() { return mes; }
    public void setMes(Integer mes) { this.mes = mes; }
    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }
    public BigDecimal getSalarioBruto() { return salarioBruto; }
    public void setSalarioBruto(BigDecimal salarioBruto) { this.salarioBruto = salarioBruto; }
    public BigDecimal getSalarioLiquido() { return salarioLiquido; }
    public void setSalarioLiquido(BigDecimal salarioLiquido) { this.salarioLiquido = salarioLiquido; }
    public BigDecimal getTotalDescontos() { return totalDescontos; }
    public void setTotalDescontos(BigDecimal totalDescontos) { this.totalDescontos = totalDescontos; }
    public List<DescontoFixoDTO> getDescontos() { return descontos; }
    public void setDescontos(List<DescontoFixoDTO> descontos) { this.descontos = descontos; }
    public List<String> getInsights() { return insights; }
    public void setInsights(List<String> insights) { this.insights = insights; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
}
