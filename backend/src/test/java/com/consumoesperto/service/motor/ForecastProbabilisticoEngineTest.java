package com.consumoesperto.service.motor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastProbabilisticoEngineTest {

    @Test
    void mesPositivoAltaComSaldoProjetadoConfortavel() {
        MotorFinanceiroSnapshot s = new MotorFinanceiroSnapshot(
            1L, bd(20000), bd(10000), bd(8000), bd(5000), bd(3500),
            bd(8000), bd(3500), bd(500), bd(10000), bd(15),
            bd(4), List.of(bd(5000), bd(5100), bd(4900), bd(5000), bd(5050), bd(4950)),
            List.of(bd(8000), bd(8000), bd(8000), bd(8000), bd(8000), bd(8000)),
            3, 3, 0, 0, 0, bd(5), bd(50), List.of()
        );
        var r = ForecastProbabilisticoEngine.calcular(s);
        assertTrue(r.chanceMesPositivoPct() >= 70);
        assertTrue(r.chanceChequeEspecialPct() <= 20);
        assertTrue(r.explicacaoDeterministica().contains("positivo"));
    }

    @Test
    void chanceOrcamentoRefleteEstourados() {
        MotorFinanceiroSnapshot s = new MotorFinanceiroSnapshot(
            1L, bd(5000), bd(1000), bd(5000), bd(-500), bd(5500),
            bd(5000), bd(5500), bd(2000), bd(5000), bd(50),
            bd(0.5), List.of(bd(5500), bd(5600), bd(5400), bd(5500), bd(5600), bd(5500)),
            List.of(bd(5000), bd(5000), bd(5000), bd(5000), bd(5000), bd(5000)),
            4, 1, 3, 0, 0, bd(20), bd(30), List.of()
        );
        var r = ForecastProbabilisticoEngine.calcular(s);
        assertTrue(r.chanceEstourarOrcamentoPct() >= 50);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
