package com.consumoesperto.whatsapp;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhatsappReconexaoBackoffTest {

    private static final int[] STEPS = {1, 2, 5, 15, 30};

    @Test
    void primeiraTentativaEImediata() {
        assertEquals(Duration.ZERO, WhatsappReconexaoBackoff.esperaAposFalhas(0, STEPS));
    }

    @Test
    void degrausExponenciais() {
        assertEquals(Duration.ofMinutes(1), WhatsappReconexaoBackoff.esperaAposFalhas(1, STEPS));
        assertEquals(Duration.ofMinutes(2), WhatsappReconexaoBackoff.esperaAposFalhas(2, STEPS));
        assertEquals(Duration.ofMinutes(5), WhatsappReconexaoBackoff.esperaAposFalhas(3, STEPS));
        assertEquals(Duration.ofMinutes(15), WhatsappReconexaoBackoff.esperaAposFalhas(4, STEPS));
        assertEquals(Duration.ofMinutes(30), WhatsappReconexaoBackoff.esperaAposFalhas(5, STEPS));
        assertEquals(Duration.ofMinutes(30), WhatsappReconexaoBackoff.esperaAposFalhas(9, STEPS));
    }
}
