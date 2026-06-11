package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationParserServiceTest {

    @Test
    void inferirBancoDoPacoteNubank() {
        assertEquals("nubank", NotificationParserService.inferirBancoDoPacote("com.nu.production"));
    }

    @Test
    void inferirBancoDoPacoteItau() {
        assertEquals("itau", NotificationParserService.inferirBancoDoPacote("com.itau"));
    }

    @Test
    void inferirBancoDoPacoteInter() {
        assertEquals("inter", NotificationParserService.inferirBancoDoPacote("br.com.intermedium"));
    }

    @Test
    void inferirBancoDoPacoteMercadoPago() {
        assertEquals("mercadopago", NotificationParserService.inferirBancoDoPacote("com.mercadopago.wallet"));
    }
}
