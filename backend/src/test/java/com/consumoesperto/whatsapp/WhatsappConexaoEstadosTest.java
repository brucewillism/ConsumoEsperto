package com.consumoesperto.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappConexaoEstadosTest {

    @Test
    void normalizaOpenEClose() {
        assertEquals(WhatsappConexaoEstados.OPEN, WhatsappConexaoEstados.normalizar("connected"));
        assertEquals(WhatsappConexaoEstados.CLOSE, WhatsappConexaoEstados.normalizar("disconnected"));
        assertEquals(WhatsappConexaoEstados.CONNECTING, WhatsappConexaoEstados.normalizar("connecting"));
        assertTrue(WhatsappConexaoEstados.isSessaoOk("open"));
        assertTrue(WhatsappConexaoEstados.isQueda("close"));
        assertFalse(WhatsappConexaoEstados.isQueda("connecting"));
    }
}
