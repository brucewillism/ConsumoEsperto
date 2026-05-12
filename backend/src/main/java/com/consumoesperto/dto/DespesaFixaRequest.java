package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class DespesaFixaRequest {

    @NotBlank
    @Size(max = 200)
    private String descricao;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal valor;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer diaVencimento;

    @Size(max = 120)
    private String categoria;
}
