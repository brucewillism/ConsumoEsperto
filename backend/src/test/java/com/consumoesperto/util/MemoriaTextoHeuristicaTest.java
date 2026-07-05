package com.consumoesperto.util;

import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaTipo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Extração heurística (sem LLM) de metadados de memória — Bloco 2.1. */
class MemoriaTextoHeuristicaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 15);

    @Test
    void planoFuturoComMesEValor_extraiTudo() {
        String texto = "vou gastar R$ 2.000,00 em julho com a cirurgia";
        assertEquals(MemoriaTipo.PLANO_FUTURO, MemoriaTextoHeuristica.detectarTipo(texto, HOJE));

        MemoriaMetadados meta = MemoriaTextoHeuristica.enriquecer(
            MemoriaMetadados.inferido(MemoriaTipo.PLANO_FUTURO), texto, HOJE);
        assertEquals(0, new BigDecimal("2000.00").compareTo(meta.valor()));
        assertEquals(7, meta.mesAlvo());
        assertEquals(2026, meta.anoAlvo());
        // «cirurgia em julho» expira em agosto
        assertEquals(LocalDate.of(2026, 8, 1), meta.validade());
    }

    @Test
    void mesQueVem_resolveMesEAno() {
        int[] alvo = MemoriaTextoHeuristica.extrairMesAnoAlvo("mês que vem vou viajar e gastar uns 1500", HOJE);
        assertNotNull(alvo);
        assertEquals(4, alvo[0]);
        assertEquals(2026, alvo[1]);
    }

    @Test
    void mesJaPassadoNoAno_assumeProximoAno() {
        int[] alvo = MemoriaTextoHeuristica.extrairMesAnoAlvo("vou pagar a matrícula em janeiro", HOJE);
        assertNotNull(alvo);
        assertEquals(1, alvo[0]);
        assertEquals(2027, alvo[1]);
    }

    @Test
    void valorEmMil_normaliza() {
        assertEquals(0, new BigDecimal("15000.00")
            .compareTo(MemoriaTextoHeuristica.extrairValor("planejo trocar de moto, uns 15 mil")));
        assertEquals(0, new BigDecimal("1500.00")
            .compareTo(MemoriaTextoHeuristica.extrairValor("vou gastar uns 1500 reais na viagem")));
    }

    @Test
    void preferencia_detectada() {
        assertEquals(MemoriaTipo.PREFERENCIA,
            MemoriaTextoHeuristica.detectarTipo("prefiro economizar em delivery durante a semana", HOJE));
    }

    @Test
    void textoOperacionalComum_naoViraPlanoNemPreferencia() {
        assertEquals(MemoriaTipo.FATO,
            MemoriaTextoHeuristica.detectarTipo("registra um gasto de 50 no cartão Nubank", HOJE));
        assertNull(MemoriaTextoHeuristica.extrairMesAnoAlvo("registra um gasto de 50 no cartão", HOJE));
    }
}
