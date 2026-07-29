package com.consumoesperto.fiscal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INSS 2026 — Portaria Interministerial MPS/MF nº 13/2026 (empregado/doméstico/avulso).
 */
class CalculoFiscalInss2026Test {

    private static final LocalDate COMP = LocalDate.of(2026, 1, 15);
    private static final int ANO = 2026;

    @BeforeEach
    void reset() {
        TabelaFiscalAnoRegistry.resetCacheForTests();
    }

    @Test
    void salarioAbaixoPrimeiraFaixa() {
        BigDecimal inss = CalculoFiscalService.calcularInssEmpregado(bd("1000.00"), COMP, ANO);
        assertEquals(bd("75.00"), inss);
    }

    @Test
    void limiteExatoPrimeiraFaixa() {
        BigDecimal inss = CalculoFiscalService.calcularInssEmpregado(bd("1621.00"), COMP, ANO);
        assertEquals(bd("121.58"), inss);
    }

    @Test
    void umCentavoAcimaPrimeiraFaixa() {
        BigDecimal inss = CalculoFiscalService.calcularInssEmpregado(bd("1621.01"), COMP, ANO);
        assertEquals(bd("121.58"), inss);
    }

    @ParameterizedTest
    @CsvSource({
        "2902.84, 236.95",
        "4354.27, 411.12",
        "8475.55, 988.10"
    })
    void limiteDeCadaFaixa(String salario, String esperado) {
        assertEquals(bd(esperado), CalculoFiscalService.calcularInssEmpregado(bd(salario), COMP, ANO));
    }

    @Test
    void salarioEntreFaixas() {
        BigDecimal inss = CalculoFiscalService.calcularInssEmpregado(bd("3500.00"), COMP, ANO);
        assertTrue(inss.compareTo(bd("236.95")) > 0);
        assertTrue(inss.compareTo(bd("411.12")) < 0);
    }

    @Test
    void arredondamentoCentavos() {
        BigDecimal inss = CalculoFiscalService.calcularInssEmpregado(new BigDecimal("2500.555"), COMP, ANO);
        assertEquals(2, inss.scale());
    }

    @Test
    void valorNoTeto() {
        assertEquals(bd("988.10"), CalculoFiscalService.calcularInssEmpregado(bd("8475.55"), COMP, ANO));
    }

    @Test
    void valorAcimaDoTeto() {
        BigDecimal noTeto = CalculoFiscalService.calcularInssEmpregado(bd("8475.55"), COMP, ANO);
        BigDecimal acima = CalculoFiscalService.calcularInssEmpregado(bd("20000.00"), COMP, ANO);
        assertEquals(noTeto, acima);
    }

    @Test
    void calculoProgressivoDetalhado() {
        assertEquals(bd("368.61"), CalculoFiscalService.calcularInssEmpregado(bd("4000.00"), COMP, ANO));
    }

    @Test
    void competenciaAnteriorAVigenciaRejeita() {
        assertThrows(IllegalStateException.class, () ->
            CalculoFiscalService.calcularInssEmpregado(bd("3000"), LocalDate.of(2025, 12, 31), ANO));
    }

    @Test
    void competenciaJaneiro2026() {
        assertTrue(CalculoFiscalService.calcularInssEmpregado(bd("3000"), LocalDate.of(2026, 1, 1), ANO)
            .compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void decimoTerceiroCalculadoSeparadamente() {
        var mensal = CalculoFiscalService.calcularInssEmpregado(bd("5000.00"), COMP, ANO);
        var entrada = new CalculoFiscalService.CalculoFolhaEntrada(
            bd("5000.00"), 0, COMP, ANO,
            TabelaFiscalAnoRegistry.TipoSeguradoInss.EMPREGADO, null, true
        );
        var mem = CalculoFiscalService.calcularFolha(entrada);
        assertTrue(mem.decimoTerceiro());
        assertTrue(mem.passos().stream().anyMatch(p -> p.contains("décimo terceiro")));
        assertEquals(mensal, mem.inss());
    }

    @Test
    void tipoSeguradoNaoSuportado() {
        var entrada = new CalculoFiscalService.CalculoFolhaEntrada(
            bd("3000"), 0, COMP, ANO,
            TabelaFiscalAnoRegistry.TipoSeguradoInss.MEI, null, false
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> CalculoFiscalService.calcularFolha(entrada));
        assertEquals(CalculoFiscalService.MSG_TIPO_NAO_SUPORTADO, ex.getMessage());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
