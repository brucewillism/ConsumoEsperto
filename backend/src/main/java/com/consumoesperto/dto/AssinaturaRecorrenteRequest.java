package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class AssinaturaRecorrenteRequest {

    @NotBlank
    @Size(max = 200)
    private String nome;

    @NotNull
    @Positive
    private BigDecimal valor;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer diaVencimento;

    private Long contaDebitoPadraoId;

    private Boolean ativo;
}
