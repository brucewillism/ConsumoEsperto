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

    /** Itaú de conferência: não escreve «fatura paga» e omite o «R$» na linha do total. */
    @Test
    void detectaFaturaItauQuitadaComSaldoZeradoSemCifrao() {
        String texto = """
            Estamos lhe enviando esta fatura para simples conferência.
            Este mês não será necessário efetuar o pagamento de sua fatura, pois o saldo apresentado foi
            igual a zero.
            Resumo da fatura em R$
            Pagamentos efetuados  -1.615,98
            Lançamentos atuais 1.556,08
            Total desta fatura 0,00
            """;

        assertTrue(FaturaPdfLayoutSupport.pareceFaturaPagaNoTexto(texto));
        assertEquals(
            FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf.PAGA_NO_BANCO,
            FaturaPdfLayoutSupport.detectarSituacaoLeituraFatura(texto, null)
        );
    }

    @Test
    void avisoDeSaldoZeroSemTotalZeradoNaoMarcaComoPaga() {
        String texto = """
            Este mês você não possui valor a ser pago em outro cartão.
            Total desta fatura 1.556,08
            """;

        assertFalse(FaturaPdfLayoutSupport.pareceFaturaPagaNoTexto(texto));
    }
}
