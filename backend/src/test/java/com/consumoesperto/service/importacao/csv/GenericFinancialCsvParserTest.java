package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.model.FinancialImportFileType;
import com.consumoesperto.model.ImportedRowKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericFinancialCsvParserTest {

    private final GenericFinancialCsvParser parser = new GenericFinancialCsvParser();

    @Test
    void parseiaLayoutGenericoPontoEVirgula() {
        String csv = "Data;Descrição;Valor\n10/08/2031;SUPERMERCADO;100,00\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "extrato.csv",
            FinancialImportFileType.BANK_STATEMENT_CSV);
        assertEquals(1, r.valid());
        assertEquals(0, r.invalid());
        assertEquals("SUPERMERCADO", r.rows().get(0).getDescription());
        assertEquals(new java.math.BigDecimal("100.00"), r.rows().get(0).getAmount());
    }

    @Test
    void descricaoComVirgulaEntreAspas() {
        String csv = "Data,Descrição,Valor\n10/08/2031,\"Padaria, Centro\",12.50\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "e.csv",
            FinancialImportFileType.BANK_STATEMENT_CSV);
        assertEquals("Padaria, Centro", r.rows().get(0).getDescription());
    }

    @Test
    void linhaInvalidaNaoDerrubaArquivo() {
        String csv = "Data;Descrição;Valor\n10/08/2031;OK;10,00\nxx;sem data;abc\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "e.csv",
            FinancialImportFileType.BANK_STATEMENT_CSV);
        assertEquals(1, r.valid());
        assertEquals(1, r.invalid());
    }

    @Test
    void cabecalhoDesconhecido() {
        String csv = "Foo;Bar;Baz\n1;2;3\n";
        assertThrows(IllegalArgumentException.class, () ->
            parser.parse(csv.getBytes(StandardCharsets.UTF_8), "e.csv",
                FinancialImportFileType.NEEDS_REVIEW));
    }

    @Test
    void arquivoSemLinhas() {
        assertThrows(IllegalArgumentException.class, () ->
            parser.parse("   \n".getBytes(StandardCharsets.UTF_8), "e.csv",
                FinancialImportFileType.NEEDS_REVIEW));
    }

    @Test
    void parcelaNaDescricao() {
        String csv = "Data;Descrição;Valor\n10/08/2031;LOJA PARC 03/12;80,00\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "cartao.csv",
            FinancialImportFileType.CARD_STATEMENT_CSV);
        assertEquals(3, r.rows().get(0).getInstallmentNumber());
        assertEquals(12, r.rows().get(0).getInstallmentTotal());
    }

    @Test
    void pagamentoFaturaNaoViraCompra() {
        String csv = "Data;Histórico;Valor\n10/08/2031;PAGAMENTO FATURA CARTÃO;500,00\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "extrato.csv",
            FinancialImportFileType.BANK_STATEMENT_CSV);
        assertEquals(ImportedRowKind.PAGAMENTO_FATURA, r.rows().get(0).getTransactionType());
    }

    @Test
    void estorno() {
        String csv = "Data;Estabelecimento;Valor\n10/08/2031;ESTORNO LOJA X;80,00\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "fatura.csv",
            FinancialImportFileType.CARD_STATEMENT_CSV);
        assertEquals(ImportedRowKind.ESTORNO, r.rows().get(0).getTransactionType());
    }

    @Test
    void dezMilLinhasDentroDoLimite() {
        StringBuilder sb = new StringBuilder("Data;Descrição;Valor\n");
        for (int i = 1; i <= 10_000; i++) {
            sb.append("10/08/2031;LOJA ").append(i).append(";1,00\n");
        }
        var r = parser.parse(sb.toString().getBytes(StandardCharsets.UTF_8), "big.csv",
            FinancialImportFileType.BANK_STATEMENT_CSV);
        assertEquals(10_000, r.valid());
    }

    @Test
    void acimaDoLimiteDeLinhas() {
        StringBuilder sb = new StringBuilder("Data;Descrição;Valor\n");
        for (int i = 0; i <= GenericFinancialCsvParser.MAX_ROWS; i++) {
            sb.append("10/08/2031;X;1,00\n");
        }
        assertThrows(IllegalArgumentException.class, () ->
            parser.parse(sb.toString().getBytes(StandardCharsets.UTF_8), "big.csv",
                FinancialImportFileType.BANK_STATEMENT_CSV));
    }
}
