package com.consumoesperto.service.motor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaInteligenteEngineTest {

    @Test
    void calculaRitmoEDiferenca() {
        MotorFinanceiroSnapshot.MetaSnapshot meta = new MotorFinanceiroSnapshot.MetaSnapshot(
            1L, "Viagem", bd(12000), bd(3000),
            LocalDate.now().plusMonths(6),
            LocalDate.now().minusMonths(3),
            bd(10)
        );
        MotorFinanceiroSnapshot s = new MotorFinanceiroSnapshot(
            1L, bd(10000), bd(5000), bd(6000), bd(2000), bd(4000),
            bd(6000), bd(4000), bd(0), bd(10000), bd(20),
            bd(2), List.of(), List.of(), 0, 0, 0, 0, 0, bd(10), bd(25),
            List.of(meta)
        );
        var r = MetaInteligenteEngine.analisar(s, bd(800)).get(0);
        assertTrue(r.ritmoAtualMensal().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(r.ritmoNecessarioMensal().compareTo(r.ritmoAtualMensal()) > 0);
        assertTrue(r.diferencaMensal().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void recomendacaoUsaGastoLazerQuandoAplicavel() {
        MotorFinanceiroSnapshot.MetaSnapshot meta = new MotorFinanceiroSnapshot.MetaSnapshot(
            2L, "Reserva", bd(6000), bd(500),
            LocalDate.now().plusMonths(10),
            LocalDate.now().minusMonths(2),
            bd(5)
        );
        MotorFinanceiroSnapshot s = new MotorFinanceiroSnapshot(
            1L, bd(8000), bd(3000), bd(5000), bd(1000), bd(4000),
            bd(5000), bd(4000), bd(0), bd(8000), bd(30),
            bd(1), List.of(), List.of(), 0, 0, 0, 0, 0, bd(5), bd(10),
            List.of(meta)
        );
        var r = MetaInteligenteEngine.analisar(s, bd(400)).get(0);
        assertTrue(r.recomendacaoDeterministica().contains("lazer")
            || r.recomendacaoDeterministica().contains("Aumente aportes"));
        assertTrue(r.probabilidadeSucessoPct() >= 0 && r.probabilidadeSucessoPct() <= 100);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
