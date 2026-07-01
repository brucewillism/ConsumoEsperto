package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class DivergenciaSaldoDTO {
    Long contaId;
    Long usuarioId;
    String nomeConta;
    BigDecimal saldoPersistido;
    BigDecimal saldoCalculado;
    BigDecimal delta;
}
