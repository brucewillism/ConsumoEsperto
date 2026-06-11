package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class NotificacaoParseadaDTO {

    boolean financeira;
    String banco;
    TipoMovimento tipo;
    BigDecimal valor;
    String descricao;

    public enum TipoMovimento {
        CREDITO,
        DEBITO
    }
}
