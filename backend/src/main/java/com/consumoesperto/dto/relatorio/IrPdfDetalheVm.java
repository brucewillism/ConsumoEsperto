package com.consumoesperto.dto.relatorio;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Uma linha do anexo analítico do PDF de IR (cada lançamento de despesa no ano-calendário).
 */
@Getter
@AllArgsConstructor
public class IrPdfDetalheVm {
    private final String data;
    private final String descricao;
    private final String categoria;
    private final String cnpj;
    private final String valorFormatado;
    /** Ex.: Confirmada | Pendente */
    private final String statusConferencia;
}
