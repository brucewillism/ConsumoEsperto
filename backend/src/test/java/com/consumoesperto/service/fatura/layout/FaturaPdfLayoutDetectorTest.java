package com.consumoesperto.service.fatura.layout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FaturaPdfLayoutDetectorTest {

    private FaturaPdfLayoutDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FaturaPdfLayoutDetector(
            mock(com.consumoesperto.service.PdfTextExtractionService.class),
            List.of(
                new NubankFaturaPdfLayoutStrategy(),
                new ItauFaturaPdfLayoutStrategy(),
                new InterFaturaPdfLayoutStrategy(),
                new MercadoPagoFaturaPdfLayoutStrategy(),
                new BradescoFaturaPdfLayoutStrategy(),
                new SantanderFaturaPdfLayoutStrategy(),
                new BancoBrasilFaturaPdfLayoutStrategy(),
                new CaixaFaturaPdfLayoutStrategy(),
                new C6BankFaturaPdfLayoutStrategy(),
                new XpFaturaPdfLayoutStrategy(),
                new BancoNordesteFaturaPdfLayoutStrategy(),
                new MastercardFaturaPdfLayoutStrategy()
            ),
            new GenericoFaturaPdfLayoutStrategy()
        );
    }

    @Test
    void detectaNubank() {
        String texto = "Olá Bruce. Esta é a sua fatura de junho Nu Pagamentos Nubank Total de compras de todos os cartões";
        assertEquals(BancoFaturaLayout.NUBANK, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaItau() {
        String texto = "Fatura Itaú Unibanco data de vencimento pagamento mínimo lançamentos no cartão";
        assertEquals(BancoFaturaLayout.ITAU, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaInter() {
        String texto = "Banco Inter fatura cartão data de vencimento opções de pagamento";
        assertEquals(BancoFaturaLayout.INTER, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaMercadoPago() {
        String texto = "Mercado Pago movimentações na fatura total da fatura vencimento";
        assertEquals(BancoFaturaLayout.MERCADO_PAGO, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaBancoBrasil() {
        String texto = "Banco do Brasil lançamentos no cartão saldo fatura anterior total da fatura vencimento";
        assertEquals(BancoFaturaLayout.BANCO_BRASIL, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaBradesco() {
        String texto = "Bradesco fatura cartão lançamentos data de vencimento pagamento mínimo";
        assertEquals(BancoFaturaLayout.BRADESCO, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaSantander() {
        String texto = "Santander fatura cartão lançamentos esfera data de vencimento";
        assertEquals(BancoFaturaLayout.SANTANDER, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaCaixa() {
        String texto = "Caixa Econômica Federal fatura cartão lançamentos data de vencimento";
        assertEquals(BancoFaturaLayout.CAIXA, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaC6Bank() {
        String texto = "C6 Bank fatura cartão lançamentos data de vencimento pagamento mínimo";
        assertEquals(BancoFaturaLayout.C6_BANK, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaXp() {
        String texto = "XP Investimentos cartão xp fatura lançamentos data de vencimento";
        assertEquals(BancoFaturaLayout.XP, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaBancoNordeste() {
        String texto = "Banco do Nordeste fatura cartão lançamentos data de vencimento";
        assertEquals(BancoFaturaLayout.BANCO_NORDESTE, detector.detectarTexto(texto).layout());
    }

    @Test
    void detectaMastercardSemEmissorEspecifico() {
        String texto = "Fatura Mastercard cartão de crédito data de vencimento pagamento mínimo";
        assertEquals(BancoFaturaLayout.MASTERCARD, detector.detectarTexto(texto).layout());
    }

    @Test
    void itauVenceMastercardQuandoAmbosAparecem() {
        String texto = "Itaú Unibanco Mastercard fatura vencimento pagamento mínimo";
        assertEquals(BancoFaturaLayout.ITAU, detector.detectarTexto(texto).layout());
    }

    @Test
    void bradescoVenceMastercard() {
        String texto = "Bradesco Mastercard fatura vencimento pagamento mínimo lançamentos";
        assertEquals(BancoFaturaLayout.BRADESCO, detector.detectarTexto(texto).layout());
    }

    @Test
    void genericoQuandoNaoReconhece() {
        String texto = "documento qualquer sem fatura";
        assertEquals(BancoFaturaLayout.GENERICO, detector.detectarTexto(texto).layout());
    }
}
