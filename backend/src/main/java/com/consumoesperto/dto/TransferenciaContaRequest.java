package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransferenciaContaRequest {

    @NotNull
    private Long contaOrigemId;

    @NotNull
    private Long contaDestinoId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal valor;

    private String descricao;

    private LocalDateTime dataTransferencia;
}
