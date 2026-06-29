package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextExtractionServiceTest {

    private final PdfTextExtractionService service = new PdfTextExtractionService();

    @Test
    void detectaPdfCriptografadoPeloMarcadorEncrypt() {
        byte[] pdf = "%PDF-1.4\n/Encrypt /Filter".getBytes();
        assertTrue(service.pdfPareceCriptografado(pdf));
    }

    @Test
    void detectaEncryptNoTrailerFinalDoArquivo() {
        byte[] prefix = ("x".repeat(140_000)).getBytes();
        byte[] trailer = "/Root/Encrypt/Filter".getBytes();
        byte[] pdf = new byte[prefix.length + trailer.length];
        System.arraycopy(prefix, 0, pdf, 0, prefix.length);
        System.arraycopy(trailer, 0, pdf, prefix.length, trailer.length);
        assertTrue(service.pdfPareceCriptografado(pdf));
    }

    @Test
    void textoLegivelExigeDataEValor() {
        assertFalse(service.textoPareceFaturaLegivel("ItauDisplay-Regular font metadata only"));
        assertTrue(service.textoPareceFaturaLegivel(
            "Fatura Itaú vencimento 02/06/2026 LANÇAMENTOS 05/05 MERCADO 45,90 Total desta fatura 165,90"
                + " x".repeat(80)
        ));
    }

    @Test
    void aceitaTextoInterComLayoutRecente() {
        String inter = ("Banco Inter fatura cartao detalhamento da fatura data de vencimento 05/07/2026 "
            + "valor da fatura 632,96 lancamentos ").repeat(3);
        assertTrue(service.textoPareceFaturaLegivel(inter));
    }

    @Test
    void variantesSenhaIncluemCpfComZerosAEsquerda() {
        List<String> v = PdfTextExtractionService.montarVariantesSenha("123456");
        assertTrue(v.contains("123456"));
        assertTrue(v.contains("0000123456"));
        assertTrue(v.contains("00001"));
        assertTrue(v.contains("000012"));
    }

    @Test
    void variantesSenhaInterComZeroAEsquerda() {
        List<String> v = PdfTextExtractionService.montarVariantesSenha("12345");
        assertTrue(v.contains("012345"));
    }
}
