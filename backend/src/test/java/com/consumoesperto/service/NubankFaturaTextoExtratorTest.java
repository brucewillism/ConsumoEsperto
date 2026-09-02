package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.NubankFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.NubankFaturaTextoExtrator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NubankFaturaTextoExtratorTest {

    private static final String TRECHO_NUBANK = """
        TRANSAÇÕES DE BRUCE W M SILVA
        25 ABR
        A B Vilela Silva - Parcela 6/10
        •••• 3443
        R$ 52,00
        26 ABR
        Atacadao 150 As
        •••• 3443
        R$ 90,53
        08 MAI
        PREFEITURA DE CAMARAGIBE
        Total a pagar R$ 307,20 (valor da transação R$ 284,92 + IOF de R$ 1,68 + juros de R$ 20,60)
        26 MAI
        Bruce W M Silva
        R$ 2.425,51
        Total de compras de todos os cartões R$ 2.444,42
        """;

    @Test
    void extraiLancamentosCartaoEPixIgnorandoSubtotalPortador() {
        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(TRECHO_NUBANK, 2026);

        assertEquals(3, itens.size());
        assertEquals(new BigDecimal("52.00"), itens.get(0).getValor());
        assertEquals(6, itens.get(0).getParcelaAtual());
        assertEquals(10, itens.get(0).getTotalParcelas());
        assertEquals(new BigDecimal("90.53"), itens.get(1).getValor());
        assertEquals(new BigDecimal("307.20"), itens.get(2).getValor());
        assertTrue(itens.get(2).getDescricao().toLowerCase().contains("prefeitura"));
    }

    @Test
    void complementaLancamentosOmitidosPelaIa() {
        List<ImportacaoFaturaItemDTO> ia = new ArrayList<>();
        ia.add(item(LocalDate.of(2026, 4, 26), "Atacadao 150 As", "90.53"));

        NubankFaturaTextoExtrator.complementar(ia, TRECHO_NUBANK, 2026);
        assertEquals(3, ia.size(), "IA + complemento devem trazer os 3 lançamentos do trecho");
        List<ImportacaoFaturaItemDTO> out = new NubankFaturaPdfLayoutStrategy().sanitizarLancamentos(ia);

        assertEquals(3, out.size());
        assertEquals(new BigDecimal("449.73"), soma(out));
        assertTrue(out.stream().anyMatch(i -> i.getValor().compareTo(new BigDecimal("52.00")) == 0));
        assertTrue(out.stream().anyMatch(i -> i.getValor().compareTo(new BigDecimal("307.20")) == 0));
    }

    private static BigDecimal soma(List<ImportacaoFaturaItemDTO> itens) {
        return itens.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void extraiTotalComprasComIntervaloDeDatas() {
        String trecho = """
            Total de compras de todos os cartões, 25 JUN a 26 JUL R$ 4.038,47
            Total de compras de todos os cartões, 25 JUN a 26 JULR$ 4.038,47
            """;
        assertEquals(
            new BigDecimal("4038.47"),
            NubankFaturaTextoExtrator.extrairTotalCompras(trecho).orElseThrow()
        );
    }

    @Test
    void extraiTotalAPagarDoResumo() {
        String trecho = """
            RESUMO DA FATURA ATUAL
            Pagamento recebido −R$ 1.764,24
            Total de compras de todos os cartões, 25 JUN a 26 JUL R$ 4.038,47
            Total a pagar R$ 2.274,23
            """;
        assertEquals(
            new BigDecimal("2274.23"),
            NubankFaturaTextoExtrator.extrairTotalAPagar(trecho).orElseThrow()
        );
    }

    @Test
    void pixParceladoEmAndamentoUsaValorDaParcela() {
        String trecho = """
            Pagamentos e Financiamentos
            03 JUL
            PAMELA PRISCILA RIBEIRO DE ALCANTARA - Parcela 1/2
            Total a pagar: R$ 529,25 (valor da transação de R$ 400,00 + R$ 4,26 de IOF + juros)
            2 parcelas de R$ 264,63
            """;
        List<ImportacaoFaturaItemDTO> pix = NubankFaturaTextoExtrator.extrairPixFinanciados(trecho, 2026);
        assertEquals(1, pix.size());
        assertEquals(new BigDecimal("264.63"), pix.get(0).getValor());
        assertEquals(1, pix.get(0).getParcelaAtual());
        assertEquals(2, pix.get(0).getTotalParcelas());
    }

    @Test
    void finalizarConciliaPixParceladoComTotalCompras() {
        List<ImportacaoFaturaItemDTO> itens = new ArrayList<>();
        ImportacaoFaturaItemDTO pix = new ImportacaoFaturaItemDTO();
        pix.setDescricao("PAMELA - Parcela 1/2 (Pix/boleto no crédito)");
        pix.setParcelaAtual(1);
        pix.setTotalParcelas(2);
        pix.setValor(new BigDecimal("529.25"));
        itens.add(pix);
        ImportacaoFaturaItemDTO cartao = new ImportacaoFaturaItemDTO();
        cartao.setDescricao("Atacadao");
        cartao.setValor(new BigDecimal("3768.42"));
        itens.add(cartao);

        NubankFaturaTextoExtrator.finalizarLista(itens, "", new BigDecimal("4038.47"));
        assertEquals(new BigDecimal("4038.47"), soma(itens));
        assertEquals(new BigDecimal("270.05"), pix.getValor());
    }

    @Test
    void extraiCompraComMascaraAsterisco() {
        String trecho = """
            TRANSAÇÕES DE BRUCE W M SILVA
            26 ABR
            Atacadao 150 As
            **** 3443
            R$ 90,53
            """;
        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(trecho, 2026);
        assertEquals(1, itens.size());
        assertEquals(new BigDecimal("90.53"), itens.get(0).getValor());
    }

    @Test
    void extraiPixQuandoOpenPdfColaDataAoEstabelecimento() {
        String openPdf = """
            Pagamentos e Financiamentos-R$ 1.863,70
            02 MAIPagamento em 02 MAI?R$ 2.315,28
            08 MAIPREFEITURA DE CAMARAGIBE
            Total a pagar: R$ 307,20 (valor da transação de R$ 284,92 + R$ 1,68 de IOF + R$ 20,60 de juros).
            R$ 307,20
            15 MAIPamela Souza
            Total a pagar: R$ 64,08 (valor da transação de R$ 60,00 + R$ 0,32 de IOF + R$ 3,76 de juros).
            R$ 64,08
            20 MAIPAY2M SOLUCOES FINANCEIRAS LTDA
            Total a pagar: R$ 52,08 (valor da transação de R$ 50,00 + R$ 0,24 de IOF + R$ 1,85 de juros).
            R$ 52,09
            """;

        List<ImportacaoFaturaItemDTO> pix = NubankFaturaTextoExtrator.extrairPixFinanciados(openPdf, 2026);

        assertEquals(3, pix.size());
        assertEquals(new BigDecimal("423.36"), pix.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void extraiTotalComprasDoTexto() {
        assertEquals(
            new BigDecimal("2444.42"),
            NubankFaturaTextoExtrator.extrairTotalCompras(TRECHO_NUBANK).orElseThrow()
        );
    }

    @Test
    void mantemComprasRecorrentesDeMesmoValorEmDatasDiferentes() {
        String trecho = """
            TRANSAÇÕES DE 26 JUL A 26 AGO
            08 AGO •••• 3443 Claro Flex R$ 44,99
            24 AGO •••• 3443 Claro Flex R$ 44,99
            """;

        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(trecho, 2026);

        assertEquals(2, itens.size(), "mesma loja e mesmo valor em dias distintos são compras distintas");
        assertEquals(new BigDecimal("89.98"), soma(itens));
    }

    @Test
    void aindaDescartaDuplicataDoMesmoDia() {
        String trecho = """
            TRANSAÇÕES DE 26 JUL A 26 AGO
            08 AGO •••• 3443 Claro Flex R$ 44,99
            08 AGO •••• 3443 Claro Flex R$ 44,99
            """;

        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(trecho, 2026);

        assertEquals(1, itens.size(), "mesmo dia, valor e estabelecimento é o mesmo lançamento em duas fontes");
    }

    @Test
    void mantemLancamentoQueFechaAPaginaAntesDoRodape() {
        String trecho = """
            TRANSAÇÕES DE 26 JUL A 26 AGO
            15 AGO •••• 3443 Restaurante e Pizzari R$ 16,00
            6 de 7

            BRUCE WILLIS MARINHO DA SILVA
            FATURA 02 SET 2026 EMISSÃO E ENVIO 26 AGO 2026
            TRANSAÇÕES DE 26 JUL A 26 AGO
            17 AGO •••• 3443 Wellhub Pamela Priscil R$ 99,99
            """;

        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(trecho, 2026);

        assertEquals(2, itens.size(), "o rodapé de paginação não pode levar o lançamento embora");
        assertEquals(new BigDecimal("115.99"), soma(itens));
    }

    @Test
    void mantemLancamentoQueFechaOBlocoAntesDaSecaoDePagamentos() {
        String trecho = """
            TRANSAÇÕES DE 26 JUL A 26 AGO
            25 AGO •••• 3443 Quotidiano Cafe R$ 15,00
            Pagamentos e Financiamentos -R$ 3.009,60
            30 JUL Pagamento em 30 JUL −R$ 2.274,23
            """;

        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(trecho, 2026);

        assertEquals(1, itens.size(), "o pagamento é crédito e não entra; a compra do dia 25 entra");
        assertEquals(new BigDecimal("15.00"), itens.get(0).getValor());
    }

    @Test
    void pixParceladoNaUltimaParcelaUsaValorDaParcelaENaoOFinanciamento() {
        String trecho = """
            Pagamentos e Financiamentos
            26 JUL PAMELA PRISCILA RIBEIRO DE ALCANTARA - Parcela 2/2
            Total a pagar: R$ 529,25 (valor da transação de R$ 400,00 + R$ 4,26 de IOF
            + R$ 124,99 de juros) divididos em 2 parcelas de R$ 264,63.
            R$ 264,63
            """;

        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(trecho, 2026);

        assertEquals(1, itens.size());
        assertEquals(new BigDecimal("264.63"), itens.get(0).getValor());
        assertEquals(2, itens.get(0).getParcelaAtual());
        assertEquals(2, itens.get(0).getTotalParcelas());
    }

    private static ImportacaoFaturaItemDTO item(LocalDate data, String desc, String valor) {
        ImportacaoFaturaItemDTO dto = new ImportacaoFaturaItemDTO();
        dto.setData(data);
        dto.setDescricao(desc);
        dto.setValor(new BigDecimal(valor));
        return dto;
    }
}
