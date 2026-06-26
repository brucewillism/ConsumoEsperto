package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappPaymentMethodHeuristicsTest {

    @Test
    void despesaParceladaNoCartaoComPixNoNomeEstabelecimentoNaoEConta() {
        String msg = "registra uma despesa com o nome pix prime gestao ltda no valor de 716,28 em 2x no cartao Itau";
        assertFalse(WhatsappPaymentMethodHeuristics.indicaPagamentoEmConta(msg));
        assertTrue(WhatsappPaymentMethodHeuristics.indicaCartaoExplicito(null, msg));
    }

    @Test
    void pagamentoViaPixContinuaDetectado() {
        assertTrue(WhatsappPaymentMethodHeuristics.indicaPagamentoEmConta("paguei 50 via pix no mercado"));
        assertTrue(WhatsappPaymentMethodHeuristics.indicaPagamentoEmConta("transferencia pix de 100 reais"));
    }

    @Test
    void nomeEstabelecimentoPixSemCartaoAindaNaoEPixPagamento() {
        assertFalse(WhatsappPaymentMethodHeuristics.indicaPagamentoEmConta("compra pix prime gestao ltda 716,28"));
    }

    @Test
    void paymentMethodJsonCartaoPrevalece() {
        assertTrue(WhatsappPaymentMethodHeuristics.indicaCartaoExplicito("CARD", "paguei via pix"));
        assertFalse(WhatsappPaymentMethodHeuristics.indicaCartaoExplicito("CONTA", "no cartao itau em 2x"));
    }
}
