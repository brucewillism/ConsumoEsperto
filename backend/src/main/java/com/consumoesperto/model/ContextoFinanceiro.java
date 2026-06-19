package com.consumoesperto.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Snapshot financeiro real do usuário — números consolidados em Java antes da narração IA.
 */
@Data
@Builder
public class ContextoFinanceiro {

    /** Patrimônio líquido em contas (dinheiro disponível real). */
    private BigDecimal patrimonioLiquido;

    /** Renda líquida mensal estimada; {@code null} quando não configurada (ZERO tratado como null). */
    private BigDecimal rendaLiquidaMensal;

    /** Total mensal de despesas fixas cadastradas. */
    private BigDecimal despesasFixas;

    /** Total mensal de assinaturas ativas. */
    private BigDecimal assinaturas;

    /** Reserva de emergência atual (= patrimônio líquido disponível). */
    private BigDecimal reservaEmergencia;

    /** Gasto mensal de referência para o Escudo (despesas do mês ou média 3 meses). */
    private BigDecimal gastoMensalMedio;

    /** Escudo atual em meses (patrimônio ÷ gasto mensal), quando calculável. */
    private BigDecimal mesesReservaAtual;
}
