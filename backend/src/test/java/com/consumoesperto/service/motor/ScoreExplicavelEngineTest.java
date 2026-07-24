package com.consumoesperto.service.motor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreExplicavelEngineTest {

    @Test
    void scoreMaximoComReservaEOrcamentoVerde() {
        MotorFinanceiroSnapshot s = new MotorFinanceiroSnapshot(
            1L, bd(50000), bd(20000), bd(8000), bd(5000), bd(4000),
            bd(8000), bd(4000), bd(0), bd(20000), bd(20),
            bd(8), List.of(bd(4000), bd(4100), bd(4050), bd(4000), bd(3950), bd(4000)),
            List.of(bd(8000), bd(8000), bd(8000), bd(8000), bd(8000), bd(8000)),
            4, 4, 0, 0, 0, bd(10), bd(80), List.of()
        );
        var r = ScoreExplicavelEngine.calcular(s);
        assertTrue(r.scoreTotal() >= 85);
        assertEquals(25, r.componentes().get(0).pontos());
    }

    @Test
    void dicaCreditoSugereReducaoUtilizacao() {
        MotorFinanceiroSnapshot s = new MotorFinanceiroSnapshot(
            1L, bd(10000), bd(2000), bd(5000), bd(500), bd(4500),
            bd(5000), bd(4500), bd(3000), bd(5000), bd(65),
            bd(1), List.of(bd(4500), bd(4600), bd(4700), bd(4800), bd(4900), bd(5000)),
            List.of(bd(5000), bd(5000), bd(5000), bd(5000), bd(5000), bd(5000)),
            3, 1, 2, 3, 4, bd(30), bd(20), List.of()
        );
        var credito = ScoreExplicavelEngine.calcular(s).componentes().stream()
            .filter(c -> c.nome().equals("Uso de Crédito"))
            .findFirst()
            .orElseThrow();
        assertTrue(credito.pontos() < 20);
        assertTrue(credito.comoRecuperar().contains("30%"));
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
