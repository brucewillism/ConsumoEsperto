package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmortizacaoVpCalculoTest {

    @Test
    void jurosEconomizados_10x100a10pct_retornaVpPrice() {
        BigDecimal economia = AmortizacaoVpCalculo.calcularJurosEconomizados(
            new BigDecimal("1000.00"),
            10,
            new BigDecimal("0.10")
        );
        assertEquals(new BigDecimal("385.54"), economia);
    }

    @Test
    void jurosEconomizados_taxaZero_retornaZero() {
        assertEquals(BigDecimal.ZERO.setScale(2), AmortizacaoVpCalculo.calcularJurosEconomizados(
            new BigDecimal("1000.00"), 10, BigDecimal.ZERO));
    }

    @Test
    void jurosEconomizados_semJuros_taxaDescontoBaixa_economiaPequena() {
        BigDecimal economia = AmortizacaoVpCalculo.calcularJurosEconomizados(
            new BigDecimal("1000.00"),
            10,
            new BigDecimal("0.005")
        );
        assertTrue(economia.compareTo(new BigDecimal("50.00")) < 0);
        assertTrue(economia.compareTo(BigDecimal.ZERO) > 0);
    }
}
