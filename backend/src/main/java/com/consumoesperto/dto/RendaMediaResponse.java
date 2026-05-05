package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RendaMediaResponse {
    private BigDecimal rendaMensalMedia;
    /** true quando há receitas confirmadas no período de 3 meses. */
    private boolean calculadaDeLancamentos;
}
