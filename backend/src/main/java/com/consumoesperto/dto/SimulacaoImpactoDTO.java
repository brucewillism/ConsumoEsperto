package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SimulacaoImpactoDTO {
    private String id;
    private String descricao;
    private BigDecimal valorMensalImpacto;
    private Integer mesesImpacto;
    private String metaDescricao;
    private String icone;
    private boolean ativa;
    private String mensagem;
    private Integer impactoScore;
    private LocalDateTime criadaEm;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public Integer getImpactoScore() { return impactoScore; }
    public void setImpactoScore(Integer impactoScore) { this.impactoScore = impactoScore; }

    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
}
