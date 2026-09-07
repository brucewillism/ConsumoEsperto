package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.model.ImportedRowKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FinancialCsvSemanticsTest {

    @Test
    void negativoNaoECompraAutomaticaNoBanco() {
        ImportedRowKind k = FinancialCsvSemantics.classify("PIX recebido", "credito",
            new BigDecimal("100.00"), false);
        assertEquals(ImportedRowKind.RECEITA, k);
    }

    @Test
    void tedETransferencia() {
        assertEquals(ImportedRowKind.TRANSFERENCIA,
            FinancialCsvSemantics.classify("TED para João", null, new BigDecimal("-50"), false));
    }

    @Test
    void pixEnviadoContinuaTransferencia() {
        assertEquals(ImportedRowKind.TRANSFERENCIA,
            FinancialCsvSemantics.classify("PIX enviado", null, new BigDecimal("-30"), false));
    }

    @Test
    void pagamentoFatura() {
        assertEquals(ImportedRowKind.PAGAMENTO_FATURA,
            FinancialCsvSemantics.classify("PAGAMENTO FATURA CARTÃO NUBANK", null,
                new BigDecimal("-200"), false));
    }

    @Test
    void estornoCancelamento() {
        assertEquals(ImportedRowKind.ESTORNO,
            FinancialCsvSemantics.classify("ESTORNO COMPRA X", null, new BigDecimal("80"), true));
    }

    @Test
    void compraNoCartaoMesmoComValorNegativoNaoViraReceita() {
        ImportedRowKind k = FinancialCsvSemantics.classify("SUPERMERCADO", null,
            new BigDecimal("-100"), true);
        assertEquals(ImportedRowKind.DESPESA, k);
        assertFalse(ImportedRowKind.RECEITA.equals(k));
    }
}
