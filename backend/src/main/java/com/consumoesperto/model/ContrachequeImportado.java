package com.consumoesperto.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "contracheques_importados")
public class ContrachequeImportado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "empresa", length = 160)
    private String empresa;

    @Column(name = "mes")
    private Integer mes;

    @Column(name = "ano")
    private Integer ano;

    @Column(name = "salario_bruto", precision = 19, scale = 2)
    private BigDecimal salarioBruto;

    @Column(name = "salario_liquido", precision = 19, scale = 2)
    private BigDecimal salarioLiquido;

    @Column(name = "total_descontos", precision = 19, scale = 2)
    private BigDecimal totalDescontos;

    @Lob
    @Column(name = "descontos_json")
    private String descontosJson;

    @Lob
    @Column(name = "insights_json")
    private String insightsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_confirmacao")
    private LocalDateTime dataConfirmacao;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        if (status == null) {
            status = Status.PENDENTE;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
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
    public String getDescontosJson() { return descontosJson; }
    public void setDescontosJson(String descontosJson) { this.descontosJson = descontosJson; }
    public String getInsightsJson() { return insightsJson; }
    public void setInsightsJson(String insightsJson) { this.insightsJson = insightsJson; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public LocalDateTime getDataConfirmacao() { return dataConfirmacao; }
    public void setDataConfirmacao(LocalDateTime dataConfirmacao) { this.dataConfirmacao = dataConfirmacao; }

    public enum Status {
        PENDENTE,
        CONFIRMADO,
        CANCELADO
    }
}
