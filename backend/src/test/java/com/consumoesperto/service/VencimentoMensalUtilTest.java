package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VencimentoMensalUtilTest {

    @Test
    void venceEmTresDias() {
        LocalDate hoje = LocalDate.of(2026, 6, 7);
        assertTrue(VencimentoMensalUtil.venceEmDias(10, hoje, 3));
        assertFalse(VencimentoMensalUtil.venceEmDias(11, hoje, 3));
    }

    @Test
    void venceEmCincoDias() {
        LocalDate hoje = LocalDate.of(2026, 6, 5);
        assertTrue(VencimentoMensalUtil.venceEmDias(10, hoje, 5));
    }

    @Test
    void dia31EmAbrilVira30() {
        LocalDate hoje = LocalDate.of(2026, 3, 30);
        assertTrue(VencimentoMensalUtil.venceEmDias(31, hoje, 1));
    }

    @Test
    void diaEfetivoNoMes() {
        assertEquals(28, VencimentoMensalUtil.diaEfetivoNoMes(31, 28));
        assertEquals(30, VencimentoMensalUtil.diaEfetivoNoMes(31, 30));
    }
}
