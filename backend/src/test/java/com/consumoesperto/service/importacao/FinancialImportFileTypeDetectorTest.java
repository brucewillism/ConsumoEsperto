package com.consumoesperto.service.importacao;

import com.consumoesperto.model.FinancialImportFileType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialImportFileTypeDetectorTest {

    private final FinancialImportFileTypeDetector detector = new FinancialImportFileTypeDetector();

    @Test
    void pdfPorAssinatura() {
        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.ISO_8859_1);
        var d = detector.detect("fatura.pdf", "application/pdf", pdf);
        assertEquals(FinancialImportFileType.INVOICE_PDF, d.type());
        assertTrue(d.isPdf());
    }

    @Test
    void csvCartaoPorCabecalho() {
        String csv = "Data;Estabelecimento;Valor;Final do cartão\n10/08/2031;LOJA;10,00;1234\n";
        var d = detector.detect("fatura.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(FinancialImportFileType.CARD_STATEMENT_CSV, d.type());
    }

    @Test
    void csvBancoPorCabecalho() {
        String csv = "Data;Histórico;Valor;Débito;Crédito\n10/08/2031;PIX enviado;10,00;10,00;\n";
        var d = detector.detect("extrato.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(FinancialImportFileType.BANK_STATEMENT_CSV, d.type());
    }

    @Test
    void csvAmbiguoPedeRevisao() {
        String csv = "Data;Descrição;Valor\n10/08/2031;LOJA;10,00\n";
        var d = detector.detect("movimentos.csv", "text/plain", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(FinancialImportFileType.NEEDS_REVIEW, d.type());
    }

    @Test
    void extensaoInesperada() {
        var d = detector.detect("foto.png", "image/png", new byte[] {1, 2, 3, 4, 5});
        assertEquals(FinancialImportFileType.UNKNOWN, d.type());
    }

    @Test
    void csvVazio() {
        assertThrows(IllegalArgumentException.class,
            () -> detector.detect("a.csv", "text/csv", new byte[0]));
    }

    @Test
    void csvGrandeDemais() {
        byte[] big = new byte[FinancialImportFileTypeDetector.MAX_CSV_BYTES + 1];
        big[0] = 'A';
        assertThrows(IllegalArgumentException.class,
            () -> detector.detect("a.csv", "text/csv", big));
    }

    @Test
    void pdfComExtensaoErradaAindaEPdf() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1);
        var d = detector.detect("arquivo.bin", "application/octet-stream", pdf);
        assertEquals(FinancialImportFileType.INVOICE_PDF, d.type());
    }
}
