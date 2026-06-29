package com.consumoesperto.service.fatura.layout;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaturaPdfLayoutSupportSituacaoTest {

    @Test
    void detectaFaturaPagaNoBanco() {
        String texto = """
            Itaú Unibanco
            Total desta fatura R$ 0,00
            Fatura paga
            10/05 PIX PATRICIA 55,58
            """;
        assertEquals(
            FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf.PAGA_NO_BANCO,
            FaturaPdfLayoutSupport.detectarSituacaoLeituraFatura(texto, BigDecimal.ZERO)
        );
    }

    @Test
    void detectaFaturaAbertaComTotalPositivo() {
        String texto = """
            Itaú Unibanco
            Total desta fatura R$ 4.418,63
            LANÇAMENTOS: compras e saques
            05/05 MERCADO 100,00
            """;
        assertEquals(
            FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf.ABERTA,
            FaturaPdfLayoutSupport.detectarSituacaoLeituraFatura(texto, new BigDecimal("4418.63"))
        );
    }

    @Test
    void detectaTotalZeradoSemTextoFaturaPaga() {
        String texto = "Total desta fatura R$ 0,00\nCompras 10/05 LOJA 50,00";
        assertEquals(
            FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf.PAGA_NO_BANCO,
            FaturaPdfLayoutSupport.detectarSituacaoLeituraFatura(texto, BigDecimal.ZERO)
        );
    }

    @Test
    void pagamentoRecebidoNaoMarcaComoFaturaPaga() {
        String texto = """
            Total desta fatura R$ 4.418,63
            05/05 PAGAMENTO RECEBIDO - CREDITO 100,00
            """;
        assertFalse(FaturaPdfLayoutSupport.pareceFaturaPagaNoTexto(texto));
        assertEquals(
            FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf.ABERTA,
            FaturaPdfLayoutSupport.detectarSituacaoLeituraFatura(texto, new BigDecimal("4418.63"))
        );
    }

    @Test
    void pareceFaturaPagaComMarcadorExplicito() {
        assertTrue(FaturaPdfLayoutSupport.pareceFaturaPagaNoTexto("Resumo\nFatura paga\nTotal R$ 0,00"));
    }
}
