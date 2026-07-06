package com.consumoesperto.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão: «em quanto tá as faturas dos meus cartões?» não deve cair na listagem de cartões sem valores.
 */
class FaturasWhatsappTextoRegressionTest {

    @Test
    @DisplayName("Frase original do bug detecta consulta de valores por cartão")
    void fraseOriginalDoBug() {
        assertTrue(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "Em quanto tá as faturas dos meus cartões?"));
    }

    @Test
    @DisplayName("Variações comuns de consulta de faturas")
    void variacoesComuns() {
        assertTrue(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "quanto estão as faturas dos meus cartões"));
        assertTrue(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "valor de cada fatura dos cartões"));
        assertTrue(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "resumo das faturas dos cartões"));
    }

    @Test
    @DisplayName("Fatura de um cartão específico não dispara listagem de todos")
    void cartaoEspecifico() {
        assertFalse(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "quanto gastei no Nubank?"));
        assertFalse(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "resumo da fatura do Inter"));
    }

    @Test
    @DisplayName("Lista de cartões sem fatura não dispara consulta de valores")
    void listaCartoesSemFatura() {
        assertFalse(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "lista meus cartões"));
        assertFalse(WhatsAppCommandService.textoPedeValoresFaturasTodosCartoes(
            "quantos cartões eu tenho?"));
    }
}
