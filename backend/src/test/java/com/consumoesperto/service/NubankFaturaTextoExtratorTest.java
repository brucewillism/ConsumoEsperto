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

    private static ImportacaoFaturaItemDTO item(LocalDate data, String desc, String valor) {
        ImportacaoFaturaItemDTO dto = new ImportacaoFaturaItemDTO();
        dto.setData(data);
        dto.setDescricao(desc);
        dto.setValor(new BigDecimal(valor));
        return dto;
    }
}
