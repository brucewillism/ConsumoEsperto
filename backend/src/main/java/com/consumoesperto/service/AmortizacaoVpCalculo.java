package com.consumoesperto.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Valor Presente (tabela Price) para estimativa de juros economizados na antecipação. */
final class AmortizacaoVpCalculo {

    private static final RoundingMode ARRED = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(16, ARRED);

    private AmortizacaoVpCalculo() {}

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

    /** PMT = PV × i × (1+i)^n / ((1+i)^n − 1) — tabela Price em BigDecimal. */
    static BigDecimal calcularParcelaPrice(BigDecimal valorTomado, BigDecimal taxaMensal, int parcelas) {
        if (valorTomado == null || valorTomado.compareTo(BigDecimal.ZERO) <= 0 || parcelas <= 0) {
            return BigDecimal.ZERO.setScale(2, ARRED);
        }
        if (taxaMensal == null || taxaMensal.compareTo(BigDecimal.ZERO) <= 0) {
            return valorTomado.divide(BigDecimal.valueOf(parcelas), 2, ARRED);
        }
        BigDecimal fator = BigDecimal.ONE.add(taxaMensal, MC).pow(parcelas, MC);
        return valorTomado.multiply(taxaMensal, MC).multiply(fator, MC)
            .divide(fator.subtract(BigDecimal.ONE, MC), 2, ARRED);
    }

    /** Converte taxa anual decimal (ex. 0,12) em taxa mensal equivalente. */
    static BigDecimal taxaMensalDeAnualDecimal(BigDecimal taxaAnualDecimal) {
        if (taxaAnualDecimal == null || taxaAnualDecimal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(8, ARRED);
        }
        BigDecimal umMaisAa = BigDecimal.ONE.add(taxaAnualDecimal, MC);
        double mensal = Math.pow(umMaisAa.doubleValue(), 1.0 / 12.0) - 1.0;
        return BigDecimal.valueOf(mensal).setScale(8, ARRED);
    }

    /** Converte taxa mensal decimal (ex. 0.0188) para % a.a. */
    static BigDecimal taxaAnualPercentDeMensal(BigDecimal taxaMensal) {
        if (taxaMensal == null || taxaMensal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, ARRED);
        }
        BigDecimal umMaisI = BigDecimal.ONE.add(taxaMensal, MC);
        return umMaisI.pow(12, MC).subtract(BigDecimal.ONE, MC)
            .multiply(BigDecimal.valueOf(100), MC)
            .setScale(2, ARRED);
    }
}
