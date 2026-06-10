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
