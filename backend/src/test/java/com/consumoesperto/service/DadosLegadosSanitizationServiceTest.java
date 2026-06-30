package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DadosLegadosSanitizationServiceTest {

    @Test
    void detectaDecimoTerceiroNaDescricao() {
        assertTrue(DadosLegadosSanitizationService.pareceDecimoTerceiro("13 salario adiantamento"));
        assertTrue(DadosLegadosSanitizationService.pareceDecimoTerceiro("Décimo terceiro 2025"));
        assertFalse(DadosLegadosSanitizationService.pareceDecimoTerceiro("Salário MV INFORMATICA"));
    }

    @Test
    void resolveParcelaDecimoTerceiro() {
        assertEquals(
            "DECIMO_TERCEIRA_PRIMEIRA",
            DadosLegadosSanitizationService.resolverOrigemDecimoTerceiro("13 salario 1ª parcela")
        );
        assertEquals(
            "DECIMO_TERCEIRA_SEGUNDA",
            DadosLegadosSanitizationService.resolverOrigemDecimoTerceiro("13 salario segunda parcela")
        );
        assertEquals(
            "DECIMO_TERCEIRO_UNICO",
            DadosLegadosSanitizationService.resolverOrigemDecimoTerceiro("13 salario integral")
        );
    }

    @Test
    void detectaReceitaAvulsaNaoSalarial() {
        assertTrue(DadosLegadosSanitizationService.pareceReceitaAvulsaNaoSalarial("PIX recebido João"));
        assertTrue(DadosLegadosSanitizationService.pareceReceitaAvulsaNaoSalarial("Transferência Itaú"));
        assertFalse(DadosLegadosSanitizationService.pareceReceitaAvulsaNaoSalarial("Salário líquido (automático)"));
    }
}
