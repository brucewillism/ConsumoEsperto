package com.consumoesperto.dto;

import java.math.BigDecimal;

/** Projeção agregada por {@code emprestimo_id} (query única, sem N+1). */
public interface EmprestimoAgregadoRow {
    String getEmprestimoId();
    BigDecimal getValorTomado();
    BigDecimal getValorParcela();
    Long getParcelasTotais();
    Long getParcelasPagas();
}
