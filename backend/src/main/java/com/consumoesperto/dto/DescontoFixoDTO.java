package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DescontoFixoDTO {
    private String rotulo;
    private BigDecimal valor;
}
