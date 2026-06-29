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
    void extraiParcelaQuandoDataPrefixadaNaDescricao() {
        String texto = """
            LANÇAMENTOS: compras e saques
            15/03 AMAZON MARKETPLACE 03/12 89,90
            Total desta fatura R$ 89,90
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals(3, itens.get(0).getParcelaAtual());
        assertEquals(12, itens.get(0).getTotalParcelas());
    }

    @Test
    void ignoraMencaoGenericaEUsaSecaoComprasParceladas() {
        String texto = """
            proxima fatura estimada em breve
            Total desta fatura R$ 100,00
            Compras parceladas - proximas faturas
            05/07/2026 1.200,00
            Limite de credito
            """;
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(1, proj.size());
        assertEquals(new BigDecimal("1200.00"), proj.get(0).getValor());
    }

    @Test
    void extraiProximasFaturasSemAcentoNoPdf() {
        String texto = """
            Total desta fatura R$ 45,90
            COMPRAS PARCELADAS - PROXIMAS FATURAS
            02/07/2026 320,50
            Limite de credito
            """;
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(1, proj.size());
        assertEquals("2026-07-02", proj.get(0).getVencimento());
    }

    @Test
    void extraiLancamentoMultilinhaComParcela() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 AMAZON MARKETPLACE SAO PAULO
            03/10 89,90
            Total desta fatura R$ 89,90
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals(3, itens.get(0).getParcelaAtual());
        assertEquals(10, itens.get(0).getTotalParcelas());
    }

    @Test
    void extraiProximasFaturasMultilinha() {
        String texto = """
            Compras parceladas - proximas faturas
            05/07/2026
            1.234,56
            Limite de credito
            """;
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(1, proj.size());
        assertEquals(new BigDecimal("1234.56"), proj.get(0).getValor());
        assertEquals("2026-07-05", proj.get(0).getVencimento());
    }

    @Test
    void extraiProximasFaturas() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 MERCADO CENTRAL 45,90
            Total desta fatura R$ 45,90
            Compras parceladas - próximas faturas
            02/07/2026 320,50
            02/08/2026 180,00
            Limite de crédito
            """;
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(2, proj.size());
        assertEquals(new BigDecimal("320.50"), proj.get(0).getValor());
        assertEquals("2026-07-02", proj.get(0).getVencimento());
    }

    @Test
    void extraiLancamentoParcelaAposValorNaMesmaLinha() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 LOJA EXEMPLO 89,90 03/12
            Total desta fatura R$ 89,90
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals(3, itens.get(0).getParcelaAtual());
        assertEquals(12, itens.get(0).getTotalParcelas());
    }

    @Test
    void extraiProximasFaturasMesAnoCurto() {
        String texto = """
            Compras parceladas - proximas faturas
            07/26 1.234,56
            08/26 890,00
            Limite de credito
            """;
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(2, proj.size());
        assertEquals(new BigDecimal("1234.56"), proj.get(0).getValor());
        assertEquals("2026-07-01", proj.get(0).getVencimento());
    }

    @Test
    void extraiProximasFaturasMesAbreviado() {
        String texto = """
            Demonstrativo de compras parceladas e proximas faturas
            jul/26 500,00
            Limite de credito
            """;
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(1, proj.size());
        assertEquals(new BigDecimal("500.00"), proj.get(0).getValor());
        assertEquals("2026-07-01", proj.get(0).getVencimento());
    }

    @Test
    void complementarPropagaParcelasQuandoIaJaTrouxeLancamentos() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 LOJA EXEMPLO 89,90 03/12
            Total desta fatura R$ 89,90
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        ImportacaoFaturaItemDTO ia = new ImportacaoFaturaItemDTO();
        ia.setDescricao("LOJA EXEMPLO");
        ia.setValor(new BigDecimal("89.90"));
        ia.setData(java.time.LocalDate.of(2026, 5, 5));
        destino.add(ia);
        ItauFaturaTextoExtrator.complementar(destino, texto, 2026);
        assertEquals(3, destino.get(0).getParcelaAtual());
        assertEquals(12, destino.get(0).getTotalParcelas());
    }

    @Test
    void extraiLancamentoMultilinhaTresLinhas() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 AMAZON MARKETPLACE
            03/10
            89,90
            Total desta fatura R$ 89,90
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals(3, itens.get(0).getParcelaAtual());
        assertEquals(10, itens.get(0).getTotalParcelas());
    }

    @Test
    void naoCortaLancamentosEmProximaFaturaDoCabecalho() {
        String texto = """
            proxima fatura R$ 100,00
            resumo da conta
            LANÇAMENTOS: compras e saques
            05/05 LOJA 89,90 03/12
            06/06 OUTRA 50,00
            Total desta fatura R$ 139,90
            compras parceladas - proximas faturas
            07/26 500,00
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
        var proj = ItauFaturaTextoExtrator.extrairProximasFaturas(texto, 2026);
        assertEquals(1, proj.size());
        assertEquals(new BigDecimal("500.00"), proj.get(0).getValor());
    }

    @Test
    void complementarPropagaParcelaPorValorUnicoQuandoDescricaoDiverge() {
        String texto = """
            LANÇAMENTOS: compras e saques
            05/05 LOJA EXEMPLO 89,90 03/12
            Total desta fatura R$ 89,90
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        ImportacaoFaturaItemDTO ia = new ImportacaoFaturaItemDTO();
        ia.setDescricao("PAGAMENTO DIVERSOS");
        ia.setValor(new BigDecimal("89.90"));
        ia.setData(java.time.LocalDate.of(2026, 5, 5));
        destino.add(ia);
        ItauFaturaTextoExtrator.complementar(destino, texto, 2026);
        assertEquals(3, destino.get(0).getParcelaAtual());
        assertEquals(12, destino.get(0).getTotalParcelas());
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

    @Test
    void extraiLancamentosAposTotalZeradoFaturaPaga() {
        String texto = """
            Itaú Unibanco
            Total desta fatura R$ 0,00
            Fatura paga em 05/06/2026
            10/05 PIX PATRICIA B 03/10 55,58
            12/05 MERCADO CENTRAL 45,90
            Compras parceladas - proximas faturas
            10/07/2026 55,58
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("PATRICIA")));
        assertEquals(3, itens.stream().filter(i -> i.getDescricao().contains("PATRICIA")).findFirst().orElseThrow().getParcelaAtual());
    }

    @Test
    void complementarSubstituiListaGenericaQuandoFaturaPaga() {
        String texto = """
            Itaú Unibanco
            Total desta fatura R$ 0,00
            Fatura paga
            10/05 PIX PATRICIA B 03/10 55,58
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        destino.add(item("Lançamento da fatura", new BigDecimal("55.58"), null, null, null));
        ItauFaturaTextoExtrator.complementar(destino, texto, 2026);
        assertEquals(1, destino.size());
        assertTrue(destino.get(0).getDescricao().contains("PATRICIA"));
    }

    @Test
    void extraiComprasEProdutosServicosNaFaturaAberta() {
        String texto = """
            Itaú Unibanco
            Total desta fatura R$ 4.418,63
            Resumo da fatura
            LANÇAMENTOS: compras e saques
            05/05 MERCADO CENTRAL 2.405,15
            LANÇAMENTOS: produtos e serviços
            01/05 ANUIDADE DIFERENCIADA 2.013,48
            Total desta fatura R$ 4.418,63
            Compras parceladas - proximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
        BigDecimal soma = itens.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("4418.63"), soma);
    }

    @Test
    void naoConfundePagamentoRecebidoComFaturaPaga() {
        String texto = """
            Itaú Unibanco
            Total desta fatura R$ 4.418,63
            LANÇAMENTOS: compras e saques
            05/05 PAGAMENTO RECEBIDO - CREDITO 100,00
            06/05 LOJA EXEMPLO 200,00
            Total desta fatura R$ 4.418,63
            """;
        assertFalse(FaturaPdfLayoutSupport.pareceFaturaPagaNoTexto(texto));
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
    }

    private static ImportacaoFaturaItemDTO item(
        String desc,
        java.math.BigDecimal valor,
        java.time.LocalDate data,
        Integer parcela,
        Integer total
    ) {
        ImportacaoFaturaItemDTO i = new ImportacaoFaturaItemDTO();
        i.setDescricao(desc);
        i.setValor(valor);
        i.setData(data);
        i.setParcelaAtual(parcela);
        i.setTotalParcelas(total);
        return i;
    }
}
