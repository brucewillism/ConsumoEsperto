package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItauFaturaTextoExtratorTest {

    private static final String TEXTO = """
        Itaú Unibanco
        Fatura do cartão itau azul
        Data de vencimento 02/06/2026
        LANÇAMENTOS: compras e saques
        05/05 MERCADO CENTRAL 45,90
        10/05 POSTO IPIRANGA 02/03 120,00
        Total desta fatura R$ 165,90
        Pagamento mínimo R$ 25,00
        """;

    @Test
    void extraiTotalEPagamentoMinimo() {
        assertEquals(new BigDecimal("165.90"), ItauFaturaTextoExtrator.extrairTotalFatura(TEXTO).orElseThrow());
        assertEquals(new BigDecimal("25.00"), ItauFaturaTextoExtrator.extrairPagamentoMinimo(TEXTO).orElseThrow());
    }

    @Test
    void extraiLancamentosComParcela() {
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(TEXTO, 2026);
        assertEquals(2, itens.size());
        assertEquals("MERCADO CENTRAL", itens.get(0).getDescricao());
        assertEquals(new BigDecimal("45.90"), itens.get(0).getValor());
        assertEquals(2, itens.get(1).getParcelaAtual());
        assertEquals(3, itens.get(1).getTotalParcelas());
    }

    @Test
    void complementaListaVaziaDaIa() {
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        ItauFaturaTextoExtrator.complementar(destino, TEXTO, 2026);
        assertFalse(destino.isEmpty());
        assertEquals(2, destino.size());
    }

    @Test
    void extraiEncargosFinanceirosSemData() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 MERCADO CENTRAL 45,90
            Encargos financeiros
            IOF OPER CREDITO 133,15
            Total desta fatura R$ 179,05
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
        BigDecimal soma = itens.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("179.05"), soma);
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().toUpperCase().contains("IOF")));
    }

    @Test
    void complementarInjetaEncargosQuandoIaOmitiu() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 MERCADO CENTRAL 45,90
            Encargos financeiros
            IOF OPER CREDITO 133,15
            Total desta fatura R$ 179,05
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        ImportacaoFaturaItemDTO compra = new ImportacaoFaturaItemDTO();
        compra.setDescricao("MERCADO CENTRAL");
        compra.setValor(new BigDecimal("45.90"));
        destino.add(compra);
        ItauFaturaTextoExtrator.complementar(destino, texto, 2026);
        BigDecimal soma = destino.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("179.05"), soma);
        assertEquals(2, destino.size());
    }
}
