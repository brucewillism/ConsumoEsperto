package com.consumoesperto.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ImportacaoConfirmacaoDTO {
    private int criadas;
    private int conciliadas;
    private int futuras;
    private int registrosNaFaturaAtual;
    private int duplicadas;
    private int matched;
    private int revisao;
    private int falhas;
    private int processadas;
    private String mensagem;
    private List<FaturaImpactoDTO> faturas = new ArrayList<>();

    public int getCriadas() { return criadas; }
    public void setCriadas(int criadas) { this.criadas = criadas; }

    public int getConciliadas() { return conciliadas; }
    public void setConciliadas(int conciliadas) { this.conciliadas = conciliadas; }

    public int getFuturas() { return futuras; }
    public void setFuturas(int futuras) { this.futuras = futuras; }

    public int getRegistrosNaFaturaAtual() { return registrosNaFaturaAtual; }
    public void setRegistrosNaFaturaAtual(int registrosNaFaturaAtual) { this.registrosNaFaturaAtual = registrosNaFaturaAtual; }

    public int getDuplicadas() { return duplicadas; }
    public void setDuplicadas(int duplicadas) { this.duplicadas = duplicadas; }

    public int getMatched() { return matched; }
    public void setMatched(int matched) { this.matched = matched; }

    public int getRevisao() { return revisao; }
    public void setRevisao(int revisao) { this.revisao = revisao; }

    public int getFalhas() { return falhas; }
    public void setFalhas(int falhas) { this.falhas = falhas; }

    public int getProcessadas() { return processadas; }
    public void setProcessadas(int processadas) { this.processadas = processadas; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public List<FaturaImpactoDTO> getFaturas() { return faturas; }
    public void setFaturas(List<FaturaImpactoDTO> faturas) { this.faturas = faturas; }

    public static class FaturaImpactoDTO {
        private Long faturaId;
        private String rotulo;
        private String periodo;
        private int transacoesAdicionadas;
        private BigDecimal totalAntes;
        private BigDecimal totalDepois;

        public Long getFaturaId() { return faturaId; }
        public void setFaturaId(Long faturaId) { this.faturaId = faturaId; }

        public String getRotulo() { return rotulo; }
        public void setRotulo(String rotulo) { this.rotulo = rotulo; }

        public String getPeriodo() { return periodo; }
        public void setPeriodo(String periodo) { this.periodo = periodo; }

        public int getTransacoesAdicionadas() { return transacoesAdicionadas; }
        public void setTransacoesAdicionadas(int transacoesAdicionadas) { this.transacoesAdicionadas = transacoesAdicionadas; }

        public BigDecimal getTotalAntes() { return totalAntes; }
        public void setTotalAntes(BigDecimal totalAntes) { this.totalAntes = totalAntes; }

        public BigDecimal getTotalDepois() { return totalDepois; }
        public void setTotalDepois(BigDecimal totalDepois) { this.totalDepois = totalDepois; }
    }
}
