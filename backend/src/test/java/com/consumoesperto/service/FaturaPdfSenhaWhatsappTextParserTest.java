package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaturaPdfSenhaWhatsappTextParserTest {

    @Test
    void comandoUsuarioItau() {
        var p = FaturaPdfSenhaWhatsappTextParser.parseComando(
            "exporte essa fatura para o banco itau o codigo para abrir a fatura e 1234");
        assertNotNull(p);
        assertEquals("1234", p.senha());
        assertEquals("Itaú", p.banco());
    }

    @Test
    void importeComSenhaCpf() {
        var p = FaturaPdfSenhaWhatsappTextParser.parseComando(
            "importe essa fatura itau codigo 12345");
        assertNotNull(p);
        assertEquals("12345", p.senha());
        assertEquals("Itaú", p.banco());
    }

    @Test
    void apenasDigitos() {
        assertEquals("12345", FaturaPdfSenhaWhatsappTextParser.extrairSenhaSolta("12345"));
    }

    @Test
    void legendaPdfComSenha() {
        assertEquals("67890", FaturaPdfSenhaWhatsappTextParser.extrairSenhaSolta(
            "senha 67890"));
    }

    @Test
    void textoSemComando() {
        assertNull(FaturaPdfSenhaWhatsappTextParser.parseComando("despesa 50 mercado"));
    }

    @Test
    void detectaErroProtegido() {
        assertTrue(FaturaPdfSenhaWhatsappTextParser.pareceErroPdfProtegido(
            PdfTextExtractionService.mensagemPdfProtegidoItau(null)));
    }
}
