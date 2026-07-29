package com.consumoesperto.fiscal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exemplos oficiais RFB — Lei 15.270/2025 (competência jan/2026).
 * Fonte: https://www.gov.br/receitafederal/pt-br/assuntos/meu-imposto-de-renda/tabelas/exemplos-de-aplicacao-da-lei-15-270-2025
 */
class CalculoFiscalIrrf2026ExemplosOficiaisTest {

    private static final LocalDate COMP = LocalDate.of(2026, 1, 2);
    private static final int ANO = 2026;

    @BeforeEach
    void reset() {
        TabelaFiscalAnoRegistry.resetCacheForTests();
    }

    @Test
    void exemplo1AliquotaZero() {
        assertExemplo(bd("3036.00"), bd("257.73"), 0, bd("0.00"));
    }

    @Test
    void exemplo2RendaAte5000() {
        assertExemplo(bd("4000.00"), bd("373.41"), 0, bd("0.00"));
    }

    @Test
    void exemplo3RendaExatamente5000() {
        assertExemplo(bd("5000.00"), bd("509.60"), 0, bd("0.00"));
    }

    @Test
    void exemplo4RendaAcima5000ComReducaoParcial() {
        assertExemplo(bd("6000.00"), bd("649.60"), 0, bd("382.88"));
    }

    @Test
    void exemplo5RendaSemReducao() {
        assertExemplo(bd("7607.20"), bd("0.00"), 0, bd("1016.27"));
    }

    @Test
    void rendimentoUmCentavoAcima5000() {
        var mem = calcular(bd("5000.01"), bd("509.61"), 0);
        assertEquals(bd("0.00"), mem.irrfFinal());
    }

    @Test
    void dependentesReduzemImposto() {
        var sem = calcular(bd("6000.00"), bd("649.60"), 0);
        var com = calcular(bd("6000.00"), bd("649.60"), 2);
        assertTrue(com.irrfFinal().compareTo(sem.irrfFinal()) < 0);
    }

    @Test
    void impostoAntesReducaoInferiorAReducaoMaxima() {
        var mem = calcular(bd("4000.00"), bd("373.41"), 0);
        assertEquals(bd("114.76"), mem.impostoProgressivo());
        assertEquals(bd("114.76"), mem.reducaoAdicional());
        assertEquals(bd("0.00"), mem.irrfFinal());
    }

    @Test
    void arredondamentoCentavos() {
        var mem = calcular(new BigDecimal("4500.555"), bd("420.00"), 0);
        assertEquals(2, mem.irrfFinal().scale());
    }

    private void assertExemplo(BigDecimal bruto, BigDecimal inss, int dep, BigDecimal irrfEsperado) {
        var mem = calcular(bruto, inss, dep);
        assertEquals(irrfEsperado, mem.irrfFinal(),
            () -> tabelaComparacao(bruto, irrfEsperado, mem.irrfFinal()));
    }

    private CalculoFiscalService.MemoriaCalculoFolha calcular(BigDecimal bruto, BigDecimal inss, int dep) {
        return CalculoFiscalService.calcularFolha(new CalculoFiscalService.CalculoFolhaEntrada(
            bruto, dep, COMP, ANO,
            TabelaFiscalAnoRegistry.TipoSeguradoInss.EMPREGADO, inss, false
        ));
    }

    private static String tabelaComparacao(BigDecimal entrada, BigDecimal esperado, BigDecimal calculado) {
        BigDecimal diff = calculado.subtract(esperado);
        return String.format("""
            | Entrada | Resultado esperado | Resultado calculado | Diferença |
            | %s | %s | %s | %s |
            """, entrada, esperado, calculado, diff);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
