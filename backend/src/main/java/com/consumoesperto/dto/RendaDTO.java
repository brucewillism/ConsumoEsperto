package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class RendaDTO {
    private final Long id;
    private final String descricao;
    private final BigDecimal valor;
    private final Integer diaPagamento;
    private final Long contaDestinoId;
    private final String contaDestinoNome;
    private final BigDecimal saldoContaDestino;
    private final boolean ativa;
    private final Integer ultimoMesCredito;
    private final LocalDateTime dataCriacao;
    private final LocalDateTime dataAtualizacao;
}
