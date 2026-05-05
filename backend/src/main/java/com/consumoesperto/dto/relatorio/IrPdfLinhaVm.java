package com.consumoesperto.dto.relatorio;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Linha da declaração IR (PDF): despesas confirmadas agregadas por categoria e CNPJ.
 */
@Getter
@AllArgsConstructor
public class IrPdfLinhaVm {
    private final String categoria;
    private final String cnpj;
    private final String valorFormatado;
    private final long quantidade;
}
