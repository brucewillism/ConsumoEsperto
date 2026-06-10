package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaturaBancosTextoExtratorTest {

    @Test
    void mercadoPagoFaturaRealSomaBateComTotal() {
        String texto = """
            Mercado Pago
            Total a pagar
            R$ 661,94
            Vence em
            08/06/2026
            Limite total
            R$ 2.800,00
            Resumo da fatura
            Consumos de 30/04 a 29/05 R$ 632,96
            Total R$ 661,94
            Detalhes de consumo
            Movimentações na fatura
            01/03 Parcela da fatura de fevereiro/2026 Parcela 4 de 6 R$ 283,13
            06/04 Parcela da fatura de março/2026 Parcela 3 de 10 R$ 250,65
            10/05 Débito para pagar a fatura R$ 1,85
            13/05 Crédito por parcelamento da fatura R$ 476,93
            13/05 Parcela da fatura de abril/2026 Parcela 1 de 10 R$ 99,18
            13/05 Pagamento da fatura de abril/2026 R$ 55,00
            30/05 IOF do rotativo R$ 2,44
            30/05 Juros do rotativo R$ 14,25
            Movimentações na fatura
            30/05 Multa por atraso R$ 10,68
            30/05 Juros de mora R$ 1,61
            Parcele a fatura do seu Cartão de Crédito Mercado Pago
            """;
        BigDecimal total = MercadoPagoFaturaTextoExtrator.extrairTotalFatura(texto).orElseThrow();
        assertEquals(new BigDecimal("661.94"), total);

        List<ImportacaoFaturaItemDTO> itens = new ArrayList<>(MercadoPagoFaturaTextoExtrator.extrairLancamentos(texto, 2026));
        MercadoPagoFaturaTextoExtrator.finalizarLista(itens, texto, total, 2026);

        assertEquals(7, itens.size());
        assertFalse(itens.stream().anyMatch(i -> i.getDescricao().toLowerCase().contains("limite total")));
        assertFalse(itens.stream().anyMatch(i -> i.getDescricao().toLowerCase().contains("credito por parcelamento")));
        BigDecimal soma = itens.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, soma.compareTo(total));
    }

    @Test
    void mercadoPagoIgnoraLimiteTotal() {
        assertTrue(MercadoPagoFaturaTextoExtrator.deveIgnorarDescricao("Limite total"));
    }

    @Test
    void mercadoPagoExtraiTotalELancamentos() {
        String texto = """
            Mercado Pago
            Movimentações na fatura
            10/05 LOJA EXEMPLO R$ 120,50
            12/05 ASSINATURA STREAM R$ 29,90
            Resumo da fatura
            Total da fatura R$ 150,40
            Data de vencimento 15/06/2026
            """;
        assertEquals(new BigDecimal("150.40"), MercadoPagoFaturaTextoExtrator.extrairTotalFatura(texto).orElseThrow());
        assertEquals(2, MercadoPagoFaturaTextoExtrator.extrairLancamentos(texto, 2026).size());
    }

    @Test
    void bancoBrasilIgnoraSaldoAnterior() {
        String texto = """
            Banco do Brasil
            Lançamentos no cartão
            05/05 POSTO BR R$ 200,00
            10/05 SALDO FATURA ANTERIOR R$ 1.500,00
            Total da fatura R$ 200,00
            Data de vencimento 10/06/2026
            """;
        List<ImportacaoFaturaItemDTO> itens = BancoBrasilFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertTrue(BancoBrasilFaturaTextoExtrator.pareceSaldoAnterior("SALDO FATURA ANTERIOR"));
    }

    @Test
    void bradescoPodaProximasFaturas() {
        String texto = """
            Bradesco
            Lançamentos
            01/04 MERCADO XYZ R$ 80,00
            Próximas faturas
            01/04 MERCADO XYZ R$ 80,00
            Total da fatura R$ 80,00
            Data de vencimento 05/06/2026
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>(BradescoFaturaTextoExtrator.extrairLancamentos(texto, 2026));
        destino.add(item("MERcado XYZ dup", new BigDecimal("80.00")));
        BradescoFaturaTextoExtrator.finalizarLista(destino, texto, new BigDecimal("80.00"), 2026);
        assertEquals(1, destino.size());
        assertFalse(destino.stream().anyMatch(i -> i.getDescricao().toLowerCase().contains("dup")));
    }

    @Test
    void santanderDetectaEncargoSimulado() {
        assertTrue(FaturaTextoExtratorPadrao.pareceEncargoComum("Valor total de juros e encargos"));
    }

    private static ImportacaoFaturaItemDTO item(String desc, BigDecimal valor) {
        ImportacaoFaturaItemDTO i = new ImportacaoFaturaItemDTO();
        i.setDescricao(desc);
        i.setValor(valor);
        return i;
    }
}
