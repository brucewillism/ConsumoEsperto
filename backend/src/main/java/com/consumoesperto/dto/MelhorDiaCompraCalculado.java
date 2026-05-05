package com.consumoesperto.dto;

import java.time.LocalDate;

/**
 * Resultado da estratégia de fechamento de fatura (vencimento − N dias corridos), isolado por cartão/usuário.
 */
public record MelhorDiaCompraCalculado(
    LocalDate proximoVencimentoCiclo,
    LocalDate dataFechamentoEstimada,
    /** Vencimento em que compras feitas no dia de fechamento tendem a cair (próxima fatura). */
    LocalDate vencimentoPagamentoComprasNoDiaFechamento,
    long diasCorridosAteFechamento,
    long diasCorridosAteVencimentoCiclo,
    boolean hojeEhDiaDeFechamento,
    int diasEntreFechamentoEVencimentoUsados
) {

    public boolean fechamentoEmUmATresDias() {
        return diasCorridosAteFechamento >= 1 && diasCorridosAteFechamento <= 3;
    }
}
