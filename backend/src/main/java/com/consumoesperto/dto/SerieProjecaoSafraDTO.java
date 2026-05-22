package com.consumoesperto.dto;

import java.util.ArrayList;
import java.util.List;

public class SerieProjecaoSafraDTO {
    private List<ProjecaoMesResumoDTO> meses = new ArrayList<>();

    public List<ProjecaoMesResumoDTO> getMeses() { return meses; }
    public void setMeses(List<ProjecaoMesResumoDTO> meses) { this.meses = meses; }
}
