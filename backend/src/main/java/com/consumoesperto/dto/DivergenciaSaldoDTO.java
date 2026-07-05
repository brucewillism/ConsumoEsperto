package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Value
@Builder
public class DivergenciaSaldoDTO {
    Long contaId;
    Long usuarioId;
    String nomeConta;
    BigDecimal saldoPersistido;
    BigDecimal saldoCalculado;
    BigDecimal delta;
    /** Última linha do audit trail — aponta onde o saldo mudou pela última vez. */
    OffsetDateTime ultimaMovimentacaoEm;
    BigDecimal saldoAposUltimaMovimentacao;
    String origemUltimaMovimentacao;
    String tipoUltimaMovimentacao;
}
