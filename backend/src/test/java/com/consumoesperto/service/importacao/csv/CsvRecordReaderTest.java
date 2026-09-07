package com.consumoesperto.service.importacao.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvRecordReaderTest {

    @Test
    void pontoEVirgula() {
        List<List<String>> rows = CsvRecordReader.read("a;b;c\n1;2;3\n", ';');
        assertEquals(2, rows.size());
        assertEquals(List.of("1", "2", "3"), rows.get(1));
    }

    @Test
    void virgula() {
        List<List<String>> rows = CsvRecordReader.read("a,b\nx,y\n", ',');
        assertEquals("y", rows.get(1).get(1));
    }

    @Test
    void tab() {
        List<List<String>> rows = CsvRecordReader.read("a\tb\n1\t2\n", '\t');
        assertEquals("2", rows.get(1).get(1));
    }

    @Test
    void aspasComVirgulaNaDescricao() {
        List<List<String>> rows = CsvRecordReader.read("d,v\n\"Foo, Bar\",10\n", ',');
        assertEquals("Foo, Bar", rows.get(1).get(0));
        assertEquals("10", rows.get(1).get(1));
    }

    @Test
    void aspasEscapadas() {
        List<List<String>> rows = CsvRecordReader.read("d\n\"A \"\"B\"\" C\"\n", ',');
        assertEquals("A \"B\" C", rows.get(1).get(0));
    }

    @Test
    void linhaVaziaIgnorada() {
        List<List<String>> rows = CsvRecordReader.read("a;b\n\n\n1;2\n", ';');
        assertEquals(2, rows.size());
    }

    @Test
    void detectaDelimitador() {
        assertEquals(';', CsvRecordReader.detectDelimiter("Data;Valor;Desc"));
        assertEquals(',', CsvRecordReader.detectDelimiter("Data,Valor,Desc"));
        assertEquals('\t', CsvRecordReader.detectDelimiter("Data\tValor\tDesc"));
    }
}
