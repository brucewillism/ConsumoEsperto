package com.consumoesperto.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Funções puras da projeção de caixa — testáveis sem Spring.
 */
final class ProjecaoMesCaixaSupport {

    private static final RoundingMode ARRED = RoundingMode.HALF_UP;

    private ProjecaoMesCaixaSupport() {}

    static boolean usarModoAntiSusto(int diaAtual, int diaLiminar) {
        return diaAtual >= diaLiminar;
    }

    /**
     * Gap salarial mensal respeitando dia de pagamento e receitas salariais já confirmadas.
     */
    static BigDecimal calcularGapSalarial(
        BigDecimal rendaLiquida,
        BigDecimal receitasSalariaisConfirmadas,
        int diaAtual,
        int diasNoMes,
        int diaPagamento
    ) {
        BigDecimal renda = nz(rendaLiquida);
        BigDecimal confirmadas = nz(receitasSalariaisConfirmadas);
        if (renda.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, ARRED);
        }
        int diaPagEfetivo = Math.min(Math.max(1, diaPagamento), diasNoMes);
        if (diaAtual > diaPagEfetivo && confirmadas.compareTo(renda) >= 0) {
            return BigDecimal.ZERO.setScale(2, ARRED);
        }
        return renda.subtract(confirmadas).max(BigDecimal.ZERO).setScale(2, ARRED);
    }

    /**
     * Remove sobreposição 13º confirmado (já no patrimônio) × PREVISTO fiscal no mesmo mês.
     */
    static BigDecimal deduplicarReceitasFiscais(
        BigDecimal receitasFiscaisBrutas,
        BigDecimal decimoTerceiroPrevisto,
        BigDecimal decimoTerceiroConfirmado,
        BigDecimal provisoesFantasmas
    ) {
        BigDecimal bruto = nz(receitasFiscaisBrutas);
        BigDecimal previsto = nz(decimoTerceiroPrevisto);
        BigDecimal confirmado = nz(decimoTerceiroConfirmado);
        BigDecimal fantasmas = nz(provisoesFantasmas);
        BigDecimal overlap = confirmado.min(previsto);
        return bruto.subtract(fantasmas).subtract(overlap).max(BigDecimal.ZERO).setScale(2, ARRED);
    }

    /**
     * Despesas previstas no fim do mês: fixas + empréstimo + margem sobre gasto variável.
     */
    static BigDecimal calcularDespesasPrevistasAntiSusto(
        BigDecimal mediaDiaria,
        int diaAtual,
        int diasNoMes,
        BigDecimal despesasFixasRestantes,
        BigDecimal parcelasEmprestimoRestantes,
        BigDecimal margemVariavelPct
    ) {
        int diasRestantes = Math.max(0, diasNoMes - diaAtual);
        BigDecimal pct = margemVariavelPct != null ? margemVariavelPct : BigDecimal.TEN;
        BigDecimal margemVariavel = nz(mediaDiaria)
            .multiply(BigDecimal.valueOf(diasRestantes))
            .multiply(pct)
            .divide(BigDecimal.valueOf(100), 2, ARRED);
        return nz(despesasFixasRestantes)
            .add(nz(parcelasEmprestimoRestantes))
            .add(margemVariavel)
            .setScale(2, ARRED);
    }

    /**
     * Suaviza probabilidade de alerta quando o saldo projetado de fechamento permanece positivo.
     */
    static BigDecimal suavizarProbabilidadeComSaldoPositivo(
        BigDecimal probabilidadeBase,
        BigDecimal saldoProjetado
    ) {
        if (saldoProjetado == null || saldoProjetado.compareTo(BigDecimal.ZERO) <= 0) {
            return probabilidadeBase;
        }
        BigDecimal prob = probabilidadeBase != null ? probabilidadeBase : BigDecimal.ZERO;
        return prob.min(BigDecimal.valueOf(55)).setScale(2, ARRED);
    }

    static boolean saldoProjetadoRobustoPositivo(BigDecimal saldoProjetado) {
        return saldoProjetado != null && saldoProjetado.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
