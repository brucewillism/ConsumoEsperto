package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagamentoFaturaRequest {

    @NotNull
    private Long faturaId;

    @NotNull
    private Long contaBancariaId;

    /** Se omitido, usa valor total da fatura. */
    private BigDecimal valor;

    private LocalDateTime dataPagamento;
}
