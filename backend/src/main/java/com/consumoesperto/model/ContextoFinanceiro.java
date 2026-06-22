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

    /** Soma das parcelas de empréstimo PREVISTO que vencem no mês corrente. */
    private BigDecimal parcelasEmprestimosAtivos;

    /** Reserva de emergência atual (= patrimônio líquido disponível). */
    private BigDecimal reservaEmergencia;

    /** Gasto mensal de referência para o Escudo (despesas do mês ou média 3 meses). */
    private BigDecimal gastoMensalMedio;

    /** Escudo atual em meses (patrimônio ÷ gasto mensal), quando calculável. */
    private BigDecimal mesesReservaAtual;

    /** Despesas fixas + assinaturas + parcelas de empréstimo ativas no mês. */
    public BigDecimal comprometimentoMensal() {
        return nz(despesasFixas).add(nz(assinaturas)).add(nz(parcelasEmprestimosAtivos));
    }

    /** Renda líquida menos comprometimento mensal; {@code null} se renda não configurada. */
    public BigDecimal rendaLivre() {
        if (rendaLiquidaMensal == null || rendaLiquidaMensal.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return rendaLiquidaMensal.subtract(comprometimentoMensal()).max(BigDecimal.ZERO)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Percentual da renda comprometido no mês; {@code null} se renda ausente. */
    public BigDecimal percentualRendaComprometida() {
        if (rendaLiquidaMensal == null || rendaLiquidaMensal.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return comprometimentoMensal()
            .divide(rendaLiquidaMensal, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
