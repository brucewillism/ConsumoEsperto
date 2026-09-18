package com.consumoesperto.service;

import com.consumoesperto.model.Fatura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaturaValorRestanteCaixaTest {

    @Test
    void abertaSemPagamento_restanteIntegral() {
        Fatura f = fatura("2000.00", null);
        assertEquals(0, new BigDecimal("2000.00").compareTo(f.valorRestanteCaixa()));
    }

    @Test
    void parcial_projetaSoRestante() {
        Fatura f = fatura("2000.00", "500.00");
        assertEquals(0, new BigDecimal("1500.00").compareTo(f.valorRestanteCaixa()));
        assertEquals(-1, f.valorRestanteCaixa().compareTo(new BigDecimal("2500.00")),
            "nunca fatura + pago");
    }

    @Test
    void pagoIgualDevido_restanteZero() {
        Fatura f = fatura("2000.00", "2000.00");
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(f.valorRestanteCaixa()));
    }

    @Test
    void pagoAcimaDoDevido_naoFicaNegativo() {
        Fatura f = fatura("2000.00", "2500.00");
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(f.valorRestanteCaixa()));
    }

    private static Fatura fatura(String devido, String pago) {
        Fatura f = new Fatura();
        f.setValorFatura(new BigDecimal(devido));
        f.setValorTotal(new BigDecimal(devido));
        f.setValorPago(pago == null ? null : new BigDecimal(pago));
        return f;
    }
}
