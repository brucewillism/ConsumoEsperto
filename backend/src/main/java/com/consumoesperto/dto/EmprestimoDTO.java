package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EmprestimoDTO {
    private String id;
    private BigDecimal valorTotalTomado;
    private BigDecimal valorParcela;
    private int parcelasPagas;
    private int parcelasTotais;
    private BigDecimal valorRestante;
    private BigDecimal jurosTotais;
    private BigDecimal taxaEfetivaMensal;
    private int progressoPercentual;
}
