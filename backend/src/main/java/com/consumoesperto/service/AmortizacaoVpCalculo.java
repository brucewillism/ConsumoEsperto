package com.consumoesperto.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Valor Presente (tabela Price) para estimativa de juros economizados na antecipação. */
final class AmortizacaoVpCalculo {

    private static final RoundingMode ARRED = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(16, ARRED);

    private AmortizacaoVpCalculo() {}

    /**
     * PMT = valorRestante / n; VP = PMT × (1 − (1+i)^−n) / i; economia = valorRestante − VP.
     */
    static BigDecimal calcularJurosEconomizados(
        BigDecimal valorRestante,
        int parcelasRestantes,
        BigDecimal taxaMensal
    ) {
        if (valorRestante == null || valorRestante.compareTo(BigDecimal.ZERO) <= 0 || parcelasRestantes <= 0) {
            return BigDecimal.ZERO.setScale(2, ARRED);
        }
        BigDecimal taxa = taxaMensal != null ? taxaMensal : BigDecimal.ZERO;
        if (taxa.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, ARRED);
        }
        BigDecimal pmt = valorRestante.divide(BigDecimal.valueOf(parcelasRestantes), 8, ARRED);
        BigDecimal umMaisI = BigDecimal.ONE.add(taxa, MC);
        BigDecimal fatorDesconto = BigDecimal.ONE.subtract(
            BigDecimal.ONE.divide(umMaisI.pow(parcelasRestantes, MC), MC),
            MC
        );
        BigDecimal vp = pmt.multiply(fatorDesconto, MC).divide(taxa, MC);
        return valorRestante.subtract(vp).max(BigDecimal.ZERO).setScale(2, ARRED);
    }
}
