package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjecaoMesCaixaSupportTest {

    private static final BigDecimal RENDA = new BigDecimal("8000.00");

    @Test
    void gapSalarial_dia26Pagamento30_semConfirmacao_projetaIntegral() {
        BigDecimal gap = ProjecaoMesCaixaSupport.calcularGapSalarial(
            RENDA, BigDecimal.ZERO, 26, 30, 30);
        assertEquals(new BigDecimal("8000.00"), gap);
    }

    @Test
    void gapSalarial_dia26Pagamento30_com13oAvulsoNaoSalarial_naoCancelaSalario() {
        // 13º confirmado não entra em receitasSalariaisConfirmadas — gap permanece integral
        BigDecimal gap = ProjecaoMesCaixaSupport.calcularGapSalarial(
            RENDA, BigDecimal.ZERO, 26, 30, 30);
        assertEquals(RENDA, gap);
    }

    @Test
    void gapSalarial_pagamentoDia5_jaConfirmado_noDia26_gapZero() {
        BigDecimal gap = ProjecaoMesCaixaSupport.calcularGapSalarial(
            RENDA, RENDA, 26, 30, 5);
        assertEquals(BigDecimal.ZERO.setScale(2), gap);
    }

    @Test
    void gapSalarial_pagamentoDia5_dia3_aindaProjetaSalario() {
        BigDecimal gap = ProjecaoMesCaixaSupport.calcularGapSalarial(
            RENDA, BigDecimal.ZERO, 3, 30, 5);
        assertEquals(RENDA, gap);
    }

    @Test
    void deduplicarReceitasFiscais_13confirmadoRemovePrevisto() {
        BigDecimal liquido = ProjecaoMesCaixaSupport.deduplicarReceitasFiscais(
            new BigDecimal("5000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("5000.00"),
            BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO.setScale(2), liquido);
    }

    @Test
    void deduplicarReceitasFiscais_13confirmadoParcial_subtraiOverlap() {
        BigDecimal liquido = ProjecaoMesCaixaSupport.deduplicarReceitasFiscais(
            new BigDecimal("5000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("3000.00"),
            BigDecimal.ZERO);
        assertEquals(new BigDecimal("2000.00"), liquido);
    }

    @Test
    void deduplicarReceitasFiscais_irPrevistoPermaneceCom13Confirmado() {
        BigDecimal liquido = ProjecaoMesCaixaSupport.deduplicarReceitasFiscais(
            new BigDecimal("5500.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("5000.00"),
            BigDecimal.ZERO);
        assertEquals(new BigDecimal("500.00"), liquido);
    }

    @Test
    void antiSusto_somenteAposDia25() {
        assertFalse(ProjecaoMesCaixaSupport.usarModoAntiSusto(24, 25));
        assertTrue(ProjecaoMesCaixaSupport.usarModoAntiSusto(25, 25));
        assertTrue(ProjecaoMesCaixaSupport.usarModoAntiSusto(26, 25));
    }

    @Test
    void antiSusto_calculaFixasEmprestimoEMargem10Pct() {
        BigDecimal media = new BigDecimal("100.00");
        BigDecimal despesas = ProjecaoMesCaixaSupport.calcularDespesasPrevistasAntiSusto(
            media,
            26,
            30,
            new BigDecimal("500.00"),
            new BigDecimal("200.00"),
            new BigDecimal("10"));
        // 4 dias restantes × 100 × 10% = 40 + 500 + 200 = 740
        assertEquals(new BigDecimal("740.00"), despesas);
    }

    @Test
    void suavizarProbabilidade_saldoPositivo_cap55() {
        BigDecimal suavizada = ProjecaoMesCaixaSupport.suavizarProbabilidadeComSaldoPositivo(
            new BigDecimal("80.00"), new BigDecimal("6958.66"));
        assertEquals(new BigDecimal("55.00"), suavizada);
    }

    @Test
    void suavizarProbabilidade_saldoNegativo_mantemOriginal() {
        BigDecimal original = new BigDecimal("80.00");
        assertEquals(original, ProjecaoMesCaixaSupport.suavizarProbabilidadeComSaldoPositivo(
            original, new BigDecimal("-100.00")));
    }
}
