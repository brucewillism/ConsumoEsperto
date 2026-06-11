package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Projeção de total de fatura futura (ex.: secção «próximas faturas» do Itaú). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjecaoFaturaMesDTO {
    /** Vencimento no formato yyyy-MM-dd */
    private String vencimento;
    private BigDecimal valor;
}
