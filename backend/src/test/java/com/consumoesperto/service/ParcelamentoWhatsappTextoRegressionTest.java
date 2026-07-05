package com.consumoesperto.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressão do bug de produção: «Registra um gasto no cartão Nubank o valor de 2599,00 em 10 vezes»
 * era lançado inteiro numa única fatura quando a IA não devolvia installmentCount.
 * O fallback textual deve reconhecer o número de parcelas direto da mensagem.
 */
class ParcelamentoWhatsappTextoRegressionTest {

    @Test
    @DisplayName("«em 10 vezes» (mensagem original do bug) devolve 10")
    void mensagemOriginalDoBug() {
        assertEquals(10, WhatsAppCommandService.extrairNumeroParcelasDoTexto(
            "Registra um gasto no cartão Nubank o valor de 2599,00 em 10 vezes"));
    }

    @Test
    @DisplayName("Variações comuns: «3x», «em 12 parcelas», «4 vezes sem juros»")
    void variacoesComuns() {
        assertEquals(3, WhatsAppCommandService.extrairNumeroParcelasDoTexto("comprei uma TV de 2000 no Inter em 3x"));
        assertEquals(12, WhatsAppCommandService.extrairNumeroParcelasDoTexto("notebook 4500 no Nubank em 12 parcelas"));
        assertEquals(4, WhatsAppCommandService.extrairNumeroParcelasDoTexto("gastei 400 no cartão em 4 vezes sem juros"));
    }

    @Test
    @DisplayName("Sem menção a parcelas devolve 0 (não inventa parcelamento)")
    void semParcelamento() {
        assertEquals(0, WhatsAppCommandService.extrairNumeroParcelasDoTexto("gastei 45,90 no mercado"));
        assertEquals(0, WhatsAppCommandService.extrairNumeroParcelasDoTexto("pix de 250 para Maria"));
        assertEquals(0, WhatsAppCommandService.extrairNumeroParcelasDoTexto(null));
    }

    @Test
    @DisplayName("«1 vez» e valores fora de 2..48 não contam como parcelamento")
    void limites() {
        assertEquals(0, WhatsAppCommandService.extrairNumeroParcelasDoTexto("paguei em 1 vez no cartão"));
        assertEquals(0, WhatsAppCommandService.extrairNumeroParcelasDoTexto("promoção 99x sem juros"));
        assertEquals(2, WhatsAppCommandService.extrairNumeroParcelasDoTexto("em 2 vezes no Nubank"));
        assertEquals(48, WhatsAppCommandService.extrairNumeroParcelasDoTexto("financiei em 48 vezes"));
    }
}
