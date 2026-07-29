package com.consumoesperto.service.legado;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompraParceladaMigracaoServiceTest {

    @Test
    void divisaoExata() {
        List<BigDecimal> p = CompraParceladaMigracaoService.distribuirParcelas(new BigDecimal("100.00"), 4);
        assertEquals(new BigDecimal("100.00"), p.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(4, p.size());
    }

    @Test
    void divisaoComSobraCentavos() {
        List<BigDecimal> p = CompraParceladaMigracaoService.distribuirParcelas(new BigDecimal("100.00"), 3);
        CompraParceladaMigracaoService.validarSomaParcelas(new BigDecimal("100.00"), p);
        assertEquals(new BigDecimal("33.34"), p.get(2));
    }

    @Test
    void quantidadeInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
            CompraParceladaMigracaoService.distribuirParcelas(new BigDecimal("100"), 0));
    }
}
