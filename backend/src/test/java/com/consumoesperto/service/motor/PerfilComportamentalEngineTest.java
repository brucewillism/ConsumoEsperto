package com.consumoesperto.service.motor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfilComportamentalEngineTest {

    @Test
    void classificaConservadorComBaixaVolatilidade() {
        MotorFinanceiroSnapshot s = snapshot(
            List.of(bd(3000), bd(3100), bd(3050), bd(3000), bd(2950), bd(3050)),
            List.of(bd(5000), bd(5100), bd(5050), bd(5000), bd(5200), bd(5100)),
            15, 0, 1
        );
        var r = PerfilComportamentalEngine.classificar(s);
        assertEquals(PerfilComportamentalEngine.Perfil.CONSERVADOR, r.perfil());
        assertTrue(r.confiancaPct() >= 50);
    }

    @Test
    void classificaImpulsivoComMuitasComprasForaOrcamento() {
        MotorFinanceiroSnapshot s = snapshot(
            List.of(bd(2000), bd(2500), bd(6000), bd(3200), bd(3100), bd(7000)),
            List.of(bd(4000), bd(4000), bd(4000), bd(4000), bd(4000), bd(4000)),
            75, 8, 6
        );
        var r = PerfilComportamentalEngine.classificar(s);
        assertEquals(PerfilComportamentalEngine.Perfil.IMPULSIVO, r.perfil());
    }

    @Test
    void classificaRendaVariavelComOscilacaoAlta() {
        MotorFinanceiroSnapshot s = snapshot(
            List.of(bd(4000), bd(4100), bd(4200), bd(4300), bd(4400), bd(4500)),
            List.of(bd(1000), bd(10000), bd(2000), bd(12000), bd(1500), bd(11000)),
            55, 3, 2
        );
        var r = PerfilComportamentalEngine.classificar(s);
        assertEquals(PerfilComportamentalEngine.Perfil.RENDA_VARIAVEL, r.perfil());
    }

    private static MotorFinanceiroSnapshot snapshot(
        List<BigDecimal> despesas,
        List<BigDecimal> receitas,
        int utilizacaoCredito,
        int parceladas,
        int foraOrcamento
    ) {
        return new MotorFinanceiroSnapshot(
            1L, bd(10000), bd(5000), bd(5000), bd(2000), bd(3500),
            bd(5000), bd(3500), bd(500), bd(10000), bd(utilizacaoCredito),
            bd(3), despesas, receitas, 5, 4, 1, parceladas, foraOrcamento,
            bd(10), bd(50), List.of()
        );
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
