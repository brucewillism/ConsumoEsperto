package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParcelamentoServiceCalculoTest {

    @Test
    void divideTotalEmNParcelasSemPerderCentavos() {
        List<BigDecimal> valores = ParcelamentoService.calcularValoresSemJuros(new BigDecimal("645.37"), 4);
        assertEquals(4, valores.size());
        BigDecimal soma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("645.37").compareTo(soma));
    }
}
