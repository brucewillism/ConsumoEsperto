package com.consumoesperto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProvisaoMemoriaDTO {
    private int diaAlvo;
    private BigDecimal valor;
    private String rotulo;
    /** Ex.: "maio/2025" */
    private String periodoHistorico;
    private String contextoOrigem;
}
