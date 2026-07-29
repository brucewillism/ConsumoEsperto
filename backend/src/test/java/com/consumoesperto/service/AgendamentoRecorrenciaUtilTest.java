package com.consumoesperto.service;

import com.consumoesperto.model.AgendamentoPagamento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgendamentoRecorrenciaUtilTest {

    @Test
    void mensalDia31FevereiroUsaUltimoDiaValido() {
        LocalDate base = LocalDate.of(2026, 1, 31);
        LocalDate prox = AgendamentoRecorrenciaUtil.calcularProximaExecucao(
            base, AgendamentoPagamento.RecorrenciaAgendamento.MENSAL, 31);
        assertEquals(LocalDate.of(2026, 2, 28), prox);
    }

    @Test
    void unicaRetornaNull() {
        assertNull(AgendamentoRecorrenciaUtil.calcularProximaExecucao(
            LocalDate.of(2026, 7, 1), AgendamentoPagamento.RecorrenciaAgendamento.UNICA, 1));
    }

    @Test
    void semanalAvancaSeteDias() {
        LocalDate base = LocalDate.of(2026, 7, 1);
        assertEquals(LocalDate.of(2026, 7, 8), AgendamentoRecorrenciaUtil.calcularProximaExecucao(
            base, AgendamentoPagamento.RecorrenciaAgendamento.SEMANAL, null));
    }
}
