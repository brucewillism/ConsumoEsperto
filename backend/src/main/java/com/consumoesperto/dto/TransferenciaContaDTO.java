package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransferenciaContaDTO {
    private Long id;
    private Long contaOrigemId;
    private String contaOrigemNome;
    private Long contaDestinoId;
    private String contaDestinoNome;
    private BigDecimal valor;
    private String descricao;
    private LocalDateTime dataTransferencia;
    private BigDecimal patrimonioLiquidoApos;
}
