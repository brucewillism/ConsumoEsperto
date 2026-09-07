package com.consumoesperto.service.importacao.csv;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvEncodingDetectorTest {

    @Test
    void utf8() {
        var r = CsvEncodingDetector.detect("Data;Valor\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("UTF-8", r.name());
        assertTrue(r.text().startsWith("Data"));
    }

    @Test
    void utf8Bom() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'D', 'a', 't', 'a'};
        var r = CsvEncodingDetector.detect(bom);
        assertEquals("UTF-8-BOM", r.name());
        assertEquals("Data", r.text());
    }

    @Test
    void windows1252QuandoNecessario() {
        byte[] mixed = new byte[] {'A', ';', (byte) 0xE7, (byte) 0xE3, 'o'};
        var r = CsvEncodingDetector.detect(mixed);
        assertEquals("windows-1252", r.name());
        assertTrue(r.text().contains("o"));
    }

    @Test
    void utf16Rejeitado() {
        byte[] le = {(byte) 0xFF, (byte) 0xFE, 'A', 0};
        assertThrows(IllegalArgumentException.class, () -> CsvEncodingDetector.detect(le));
    }

    @Test
    void vazioRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> CsvEncodingDetector.detect(new byte[0]));
    }
}
