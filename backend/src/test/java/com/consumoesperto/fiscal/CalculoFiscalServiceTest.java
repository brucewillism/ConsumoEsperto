package com.consumoesperto.fiscal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculoFiscalServiceTest {

    @BeforeEach
    void reset() {
        TabelaFiscalAnoRegistry.resetCacheForTests();
    }

    @Test
    void anoNaoCadastrado() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            CalculoFiscalService.calcular(2010, LocalDate.of(2010, 6, 1), BigDecimal.valueOf(3000), 0));
        assertEquals(CalculoFiscalService.MSG_ANO_NAO_CADASTRADO, ex.getMessage());
    }

    @Test
    void dataAntesDaVigencia2025() {
        assertThrows(IllegalStateException.class, () ->
            CalculoFiscalService.calcular(2025, LocalDate.of(2024, 12, 31), BigDecimal.valueOf(3000), 0));
    }

    @Test
    void mudancaVigenciaMaio2025() {
        var jan = TabelaFiscalAnoRegistry.obterVersao(2025, LocalDate.of(2025, 4, 30)).orElseThrow();
        var mai = TabelaFiscalAnoRegistry.obterVersao(2025, LocalDate.of(2025, 5, 1)).orElseThrow();
        assertTrue(jan.faixasIr().get(0).limiteSuperior().compareTo(mai.faixasIr().get(0).limiteSuperior()) < 0);
    }

    @Test
    void primeiraFaixaIsenta() {
        var mem = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(2000), 0);
        assertEquals(BigDecimal.ZERO.setScale(2), mem.irrf());
    }

    @Test
    void ultimaFaixaComDeducao() {
        var mem = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(8000), 0);
        assertTrue(mem.irrf().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(mem.inss().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void tetoInss() {
        var mem = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(20000), 0);
        // Contribuição máxima empregado 2025 (teto R$ 8.157,41) ≈ R$ 951,62
        assertTrue(mem.inss().compareTo(new BigDecimal("952")) < 0);
        assertTrue(mem.inss().compareTo(new BigDecimal("950")) > 0);
    }

    @Test
    void dependentesReduzemBaseIr() {
        var sem = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(5000), 0);
        var com = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(5000), 2);
        assertTrue(com.irrf().compareTo(sem.irrf()) <= 0);
    }

    @Test
    void arredondamentoCentavos() {
        var mem = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(3500.555), 0);
        assertEquals(2, mem.inss().scale());
        assertEquals(2, mem.irrf().scale());
    }

    @Test
    void memoriaCalculoContemPassos() {
        var mem = CalculoFiscalService.calcular(2025, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(4000), 1);
        assertTrue(mem.passos().size() >= 2);
    }

    @Test
    void tabelaUsadaNoResultado() {
        var rel = CalculoFiscalService.relatorioTabelaUsada(2025, LocalDate.of(2025, 3, 15));
        assertEquals(2025, rel.get("ano"));
        assertTrue(rel.containsKey("fonte"));
    }

    @Test
    void ano2026InssValidadoCalcula() {
        var mem = CalculoFiscalService.calcular(2026, LocalDate.of(2026, 3, 1), BigDecimal.valueOf(3000), 0);
        assertTrue(mem.inss().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(2, mem.inss().scale());
    }
}
