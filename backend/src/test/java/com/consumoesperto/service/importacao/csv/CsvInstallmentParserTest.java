package com.consumoesperto.service.importacao.csv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsvInstallmentParserTest {

    @Test
    void parc() {
        var r = CsvInstallmentParser.parse("LOJA PARC 03/12");
        assertEquals(3, r.number());
        assertEquals(12, r.total());
    }

    @Test
    void parenteses() {
        var r = CsvInstallmentParser.parse("LOJA (2/10)");
        assertEquals(2, r.number());
        assertEquals(10, r.total());
    }

    @Test
    void dataNaoEParcela() {
        var r = CsvInstallmentParser.parse("10/08 SUPERMERCADO");
        assertNull(r.number());
    }
}
