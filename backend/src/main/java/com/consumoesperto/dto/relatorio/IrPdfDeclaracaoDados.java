package com.consumoesperto.dto.relatorio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados agregados para o PDF de apoio ao IR (mesma base do CSV legado).
 */
public record IrPdfDeclaracaoDados(List<IrPdfLinhaVm> linhas, BigDecimal totalDespesas) {
}
