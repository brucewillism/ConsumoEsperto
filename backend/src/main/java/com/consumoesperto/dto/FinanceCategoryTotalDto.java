package com.consumoesperto.dto;

import com.consumoesperto.edith.UntrustedText;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Agregado de {@code finance.category.summary}. Sem linhas de transação. */
@Value
@Builder
public class FinanceCategoryTotalDto {
    Long categoryId;
    UntrustedText categoria;
    BigDecimal total;
}
