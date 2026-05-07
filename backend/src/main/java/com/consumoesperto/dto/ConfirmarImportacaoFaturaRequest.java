package com.consumoesperto.dto;

import java.util.List;

public class ConfirmarImportacaoFaturaRequest {
    private List<Integer> indices;

    public List<Integer> getIndices() { return indices; }
    public void setIndices(List<Integer> indices) { this.indices = indices; }
}
