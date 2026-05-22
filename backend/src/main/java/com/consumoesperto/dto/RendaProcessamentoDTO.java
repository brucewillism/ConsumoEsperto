package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RendaProcessamentoDTO {
    private final RendaDTO renda;
    private final boolean creditoAplicado;
    private final RecalculoProjecaoSazonalDTO projecaoSazonal;
}
