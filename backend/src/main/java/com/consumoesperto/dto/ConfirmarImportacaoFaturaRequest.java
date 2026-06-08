package com.consumoesperto.dto;

import java.util.List;

public class ConfirmarImportacaoFaturaRequest {
    private List<Integer> indices;
    /** Quando true, confirma mesmo que a soma dos lançamentos não bata com o total da fatura. */
    private boolean ignorarDivergencia;

    public List<Integer> getIndices() { return indices; }
    public void setIndices(List<Integer> indices) { this.indices = indices; }

    public boolean isIgnorarDivergencia() { return ignorarDivergencia; }
    public void setIgnorarDivergencia(boolean ignorarDivergencia) { this.ignorarDivergencia = ignorarDivergencia; }
}
