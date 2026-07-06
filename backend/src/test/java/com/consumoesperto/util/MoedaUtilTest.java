package com.consumoesperto.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoedaUtilTest {

    @Test
    void distribuirParcelas_somaIgualTotal() {
        List<BigDecimal> partes = MoedaUtil.distribuirParcelas(new BigDecimal("100.00"), 3);
        BigDecimal soma = partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, soma.compareTo(new BigDecimal("100.00")));
        assertEquals(3, partes.size());
        assertEquals(0, partes.get(2).compareTo(new BigDecimal("33.34")));
    }

    @Test
    void valoresProximos_percentual10_deduplicaEstimativaVsReal() {
        assertTrue(MoedaUtil.valoresProximos(
            new BigDecimal("1500.00"), new BigDecimal("1487.35"),
            new BigDecimal("10"), new BigDecimal("2.00")));
    }

    @Test
    void valoresProximos_percentual10_naoDeduplicaValoresDistantes() {
        assertFalse(MoedaUtil.valoresProximos(
            new BigDecimal("1500.00"), new BigDecimal("900.00"),
            new BigDecimal("10"), new BigDecimal("2.00")));
    }

    @Test
    void distribuirParcelas_residuoNaUltima() {
        List<BigDecimal> partes = MoedaUtil.distribuirParcelas(new BigDecimal("10.00"), 3);
        assertEquals(0, partes.get(0).compareTo(new BigDecimal("3.33")));
        assertEquals(0, partes.get(1).compareTo(new BigDecimal("3.33")));
        assertEquals(0, partes.get(2).compareTo(new BigDecimal("3.34")));
    }
}
