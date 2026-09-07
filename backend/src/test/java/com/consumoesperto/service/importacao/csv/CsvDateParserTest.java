package com.consumoesperto.service.importacao.csv;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsvDateParserTest {

    @Test
    void br() {
        assertEquals(LocalDate.of(2031, 8, 10), CsvDateParser.parse("10/08/2031"));
    }

    @Test
    void iso() {
        assertEquals(LocalDate.of(2031, 8, 10), CsvDateParser.parse("2031-08-10"));
    }

    @Test
    void invalida() {
        assertNull(CsvDateParser.parse("não é data"));
    }
}
