package com.consumoesperto.service.importacao.csv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsvAmountParserTest {

    @Test
    void reaisComPrefixo() {
        assertEquals(new BigDecimal("120.50"), CsvAmountParser.parse("R$ 120,50"));
    }

    @Test
    void formatoBr() {
        assertEquals(new BigDecimal("120.50"), CsvAmountParser.parse("120,50"));
    }

    @Test
    void formatoUs() {
        assertEquals(new BigDecimal("120.50"), CsvAmountParser.parse("120.50"));
    }

    @Test
    void negativo() {
        assertEquals(new BigDecimal("-120.50"), CsvAmountParser.parse("-120,50"));
    }

    @Test
    void parenteses() {
        assertEquals(new BigDecimal("-120.50"), CsvAmountParser.parse("(120,50)"));
    }

    @Test
    void milharBr() {
        assertEquals(new BigDecimal("1234.56"), CsvAmountParser.parse("1.234,56"));
    }

    @Test
    void milharUs() {
        assertEquals(new BigDecimal("1234.56"), CsvAmountParser.parse("1,234.56"));
    }

    @Test
    void vazio() {
        assertNull(CsvAmountParser.parse(""));
        assertNull(CsvAmountParser.parse(null));
    }
}
