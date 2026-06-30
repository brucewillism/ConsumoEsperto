package com.consumoesperto.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BancoBrasilCatalogTest {

    @Test
    void bbIdCorrespondeBancoDoBrasilExtraidoDoPdf() {
        assertTrue(BancoBrasilCatalog.bancosCorrespondem("bb", "Banco do Brasil"));
        assertTrue(BancoBrasilCatalog.bancosCorrespondem("Banco do Brasil", "bb"));
    }

    @Test
    void nubankIdCorrespondeNomeCompleto() {
        assertTrue(BancoBrasilCatalog.bancosCorrespondem("nubank", "Nubank"));
    }

    @Test
    void idCanonicoItauEInter() {
        assertEquals("itau", BancoBrasilCatalog.idCanonicoDe("Itaú").orElseThrow());
        assertEquals("itau", BancoBrasilCatalog.idCanonicoDe("itau azul").orElseThrow());
        assertEquals("inter", BancoBrasilCatalog.idCanonicoDe("Banco Inter").orElseThrow());
    }

    @Test
    void bancosDiferentesNaoCorrespondem() {
        assertFalse(BancoBrasilCatalog.bancosCorrespondem("bb", "Nubank"));
        assertFalse(BancoBrasilCatalog.bancosCorrespondem("itau", "Bradesco"));
    }
}
