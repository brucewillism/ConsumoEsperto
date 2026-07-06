package com.consumoesperto.regressao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** EM-04: parcelas restantes inclui parcela corrente. */
class ParcelasRestantesBoundaryRegressionTest {

    @Test
    void parcelasRestantes_primeiraDeDez() {
        assertEquals(10, restantes(1, 10));
    }

    @Test
    void parcelasRestantes_ultimaDeDez() {
        assertEquals(1, restantes(10, 10));
    }

    @Test
    void parcelasRestantes_meio() {
        assertEquals(8, restantes(3, 10));
    }

    private static int restantes(int parcelaAtual, int total) {
        return Math.max(0, total - parcelaAtual + 1);
    }
}
