package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacaoCelularWebhookResponse {

    public enum Status {
        PROCESSADA,
        IGNORADA,
        DUPLICADA
    }

    private Status status;
    private String mensagem;
    private Long transacaoId;
    private String banco;
    private String contaNome;
    private BigDecimal saldoAtualConta;
}
