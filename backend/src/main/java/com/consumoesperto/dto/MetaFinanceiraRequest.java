package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class MetaFinanceiraRequest {

    @NotBlank
    @Size(max = 255)
    private String descricao;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal valorTotal;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100")
    private BigDecimal percentualComprometimento;

    /** 1 = baixa, 5 = máxima. Se omitido, usa 3. */
    @Min(1)
    @Max(5)
    private Integer prioridade;
}
