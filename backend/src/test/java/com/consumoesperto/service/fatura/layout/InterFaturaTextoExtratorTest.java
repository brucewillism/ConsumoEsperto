package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterFaturaTextoExtratorTest {

    private static final String TEXTO = """
        Banco Inter
        Resumo da fatura
        Valor da fatura R$ 291,14
        Data de vencimento 02/06/2026
        Data de corte: 25/05/2026
        Detalhamento da fatura
        21/02 PARC SALDO TOT - R DO BRASIL TECNO R$ 273,14
        Parcela 04 de 06
        28/04 APPLE.COM/BILL R$ 11,50
        29/04 APPLE.COM/BILL R$ 18,00
        25/05 Total a pagar em encargos e IOF do rotativo R$ 2,97
        Próximas faturas
        21/02 PARC SALDO TOT - R DO BRASIL TECNO R$ 273,14
        Parcela 05 de 06
        21/02 PARC SALDO TOT - R DO BRASIL TECNO R$ 273,14
        Parcela 06 de 06
        Opções de pagamento
        1 + 5x R$ 71,81
        """;

    @Test
    void extraiTotalEIgnoraProximasFaturas() {
        assertEquals(new BigDecimal("291.14"), InterFaturaTextoExtrator.extrairTotalFatura(TEXTO).orElseThrow());
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(TEXTO, 2026);
        assertEquals(3, itens.size());
        assertFalse(itens.stream().anyMatch(i -> InterFaturaTextoExtrator.pareceLinhaEncargoInter(i.getDescricao())));
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("PARC SALDO")));
        assertEquals(4, itens.stream().filter(i -> i.getDescricao().contains("PARC")).findFirst().orElseThrow().getParcelaAtual());
    }

    @Test
    void complementarSubstituiListaInfladaDaIa() {
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        LocalDate fev21 = LocalDate.of(2026, 2, 21);
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), fev21, 4, 6));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("11.50"), LocalDate.of(2026, 4, 29), null, null));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("18.00"), LocalDate.of(2026, 4, 29), null, null));
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), fev21, 5, 6));
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), fev21, 6, 6));
        destino.add(item("Parcelamento 1+5x", new BigDecimal("71.81"), null, null, null));
        destino.add(item("IOF simulacao", new BigDecimal("12.00"), null, null, null));

        InterFaturaTextoExtrator.complementar(destino, TEXTO, 2026);
        assertEquals(3, destino.size());
        assertFalse(destino.stream().anyMatch(i -> i.getValor().compareTo(new BigDecimal("71.81")) == 0));
        assertEquals(1, destino.stream().filter(i -> i.getDescricao().contains("PARC")).count());
    }

    @Test
    void podaEspuriosPorDescricaoSemTextoPdf() {
        List<ImportacaoFaturaItemDTO> itens = new ArrayList<>();
        itens.add(item("R $ 0 , 0 0 DESPESAS DO MÊS", new BigDecimal("657.58"), LocalDate.of(2026, 7, 2), null, null));
        itens.add(item(
            "Despesas da fatura CARTÃO 2306****0982 Data Movimentação Beneficiário Valor 24 de jun. 2026 PAGAMENTO ON LINE",
            new BigDecimal("33.89"), LocalDate.of(2026, 7, 2), null, null));
        itens.add(item("PARC SALDO TOT", new BigDecimal("273.14"), LocalDate.of(2026, 5, 21), 5, 6));

        InterFaturaTextoExtrator.podarEspuriosPorDescricao(itens);

        assertEquals(1, itens.size());
        assertTrue(itens.get(0).getDescricao().contains("PARC SALDO"));
    }

    @Test
    void extraiComprasIndividuaisAntesSubtotalDespesasDoMes() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            12/05
            MERCADO LIVRE
            89,90
            15/05
            POSTO IPIRANGA
            120,00
            02/07 DESPESAS DO MÊS R$ 657,58
            Despesas da fatura CARTÃO 2306 Data Movimentação Beneficiário PAGAMENTO ON LINE R$ 33,89
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("MERCADO")));
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("POSTO")));
        assertFalse(itens.stream().anyMatch(i -> i.getDescricao().toUpperCase().contains("DESPESAS DO M")));
        assertFalse(itens.stream().anyMatch(i -> i.getDescricao().toUpperCase().contains("PAGAMENTO ON LINE")));
    }

    @Test
    void ignoraSimulacaoParcelamento() {
        assertTrue(InterFaturaTextoExtrator.deveIgnorarDescricao("1 + 5x R$ 71,81"));
        assertTrue(InterFaturaTextoExtrator.deveIgnorarDescricao("Opções de pagamento"));
        assertTrue(InterFaturaTextoExtrator.pareceLinhaEncargoInter(
            "Total a pagar em encargos e IOF do rotativo"));
        assertTrue(InterFaturaTextoExtrator.pareceLinhaEncargoInter(
            "Valor total de juros e encargos"));
    }

    @Test
    void ignoraResumoDespesasDoMesEComprovantePagamento() {
        assertTrue(InterFaturaTextoExtrator.deveIgnorarDescricao("R $ 0 , 0 0 DESPESAS DO MÊS"));
        assertTrue(InterFaturaTextoExtrator.deveIgnorarDescricao(
            "Despesas da fatura CARTÃO 2306****0982 Data Movimentação Beneficiário Valor 24 de jun. 2026 PAGAMENTO ON LINE"));
        assertTrue(InterFaturaTextoExtrator.pareceLinhaResumoOuComprovanteInter("DESPESAS DO MÊS"));
    }

    @Test
    void extraiSomenteComprasIgnorandoResumoInter() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            R $ 0 , 0 0 DESPESAS DO MÊS R$ 657,58
            Despesas da fatura CARTÃO 2306****0982 Data Movimentação Beneficiário Valor
            24 de jun. 2026 PAGAMENTO ON LINE - + R$ 33,89
            21/05 PARC SALDO TOT - R DO BRASIL TECNO R$ 273,14
            Parcela 05 de 06
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertTrue(itens.get(0).getDescricao().contains("PARC SALDO"));
        assertEquals(new BigDecimal("273.14"), itens.get(0).getValor());
    }

    @Test
    void extraiLancamentosQuandoProximasApareceAntesDoDetalhamento() {
        String textoDesordenado = """
            Banco Inter
            Próximas faturas (resumo)
            Valor da fatura R$ 291,14
            Data de vencimento 02/06/2026
            Data de corte: 25/05/2026
            Detalhamento da fatura
            21/02 PARC SALDO TOT - R DO BRASIL TECNO R$ 273,14
            Parcela 04 de 06
            28/04 APPLE.COM/BILL R$ 11,50
            29/04 APPLE.COM/BILL R$ 18,00
            Próximas faturas
            21/02 PARC SALDO TOT - R DO BRASIL TECNO R$ 273,14
            Parcela 05 de 06
            Opções de pagamento
            1 + 5x R$ 71,81
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(textoDesordenado, 2026);
        assertEquals(3, itens.size());
        assertEquals(1, itens.stream().filter(i -> i.getDescricao().contains("PARC")).count());
    }

    @Test
    void podaEncargosEParcelasFuturasDaIa() {
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        LocalDate fev21 = LocalDate.of(2026, 2, 21);
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), fev21, 4, 6));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("11.50"), LocalDate.of(2026, 4, 29), null, null));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("18.00"), LocalDate.of(2026, 4, 29), null, null));
        destino.add(item("Total a pagar em encargos e IOF do rotativo", new BigDecimal("2.97"), LocalDate.of(2026, 5, 25), null, null));
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), fev21, 5, 6));
        destino.add(item("Parcelamento 1+5x", new BigDecimal("71.81"), null, null, null));

        InterFaturaTextoExtrator.finalizarListaInter(destino, TEXTO, new BigDecimal("291.14"), 2026);
        assertEquals(2, destino.size());
        assertEquals(1, destino.stream().filter(i -> i.getDescricao().contains("PARC")).count());
        assertFalse(destino.stream().anyMatch(i -> i.getValor().compareTo(new BigDecimal("71.81")) == 0));
        assertEquals(
            new BigDecimal("291.14"),
            destino.stream().map(ImportacaoFaturaItemDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @Test
    void finalizarRemoveEncargosSimuladosEConciliaComTotal() {
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        LocalDate fev21 = LocalDate.of(2026, 2, 21);
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), fev21, 4, 6));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("11.50"), LocalDate.of(2026, 4, 28), null, null));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("18.00"), LocalDate.of(2026, 4, 29), null, null));
        destino.add(item("Valor total de juros e encargos", new BigDecimal("53.21"), LocalDate.of(2026, 5, 25), null, null));

        InterFaturaTextoExtrator.finalizarListaInter(destino, TEXTO, new BigDecimal("291.14"), 2026);
        assertEquals(2, destino.size());
        assertFalse(destino.stream().anyMatch(i -> i.getDescricao().toLowerCase().contains("juros")));
        assertEquals(
            new BigDecimal("291.14"),
            destino.stream().map(ImportacaoFaturaItemDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @Test
    void ignoraLinhaResumoVencimentoComTotal() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 1.015,74
            Data de vencimento 18/06/2026
            Data de corte: 10/06/2026
            Detalhamento da fatura
            18/06 R$ 1.015,74
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertTrue(itens.isEmpty());
    }

    @Test
    void extraiFormatoMultilinha() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 50,00
            Data de vencimento 18/06/2026
            Detalhamento da fatura
            12/05
            MERCADO LIVRE
            R$ 50,00
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals("MERCADO LIVRE", itens.get(0).getDescricao());
    }

    @Test
    void extraiFaturaPagaLayoutColunasValorNaLinhaSeguinte() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            21/05
            PARC SALDO TOT - R DO BRASIL TECNO
            273,14
            Parcela 05 de 06
            28/04
            APPLE.COM/BILL
            11,50
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(2, itens.size());
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("PARC SALDO")));
        assertEquals(5, itens.stream()
            .filter(i -> i.getDescricao().contains("PARC"))
            .findFirst()
            .orElseThrow()
            .getParcelaAtual());
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("APPLE")));
    }

    @Test
    void extraiTextoComCaracteresEmLinhasVerticais() {
        String textoVertical = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Detalhamento da fatura
            2
            1
            /
            0
            5
            PARC SALDO TOT
            273,14
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(textoVertical, 2026);
        assertEquals(1, itens.size());
        assertTrue(itens.get(0).getDescricao().contains("PARC SALDO"));
        assertEquals(new BigDecimal("273.14"), itens.get(0).getValor());
    }

    @Test
    void reconstruirTextoJuntaFragmentosDeData() {
        String reconstruido = InterFaturaTextoExtrator.reconstruirTextoPdfInter("""
            2
            1
            /
            0
            5
            teste
            """);
        assertTrue(reconstruido.contains("21/05"));
    }

    @Test
    void extraiFaturaPagaDataDescricaoValorEmLinhasSeparadas() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Detalhamento da fatura
            21/05
            PARC SALDO TOT
            273,14
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals(new BigDecimal("273.14"), itens.get(0).getValor());
    }

    @Test
    void complementarSubstituiListaGenericaDaIaQuandoFaturaPaga() {
        String textoPago = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            21/05 PARC SALDO TOT - R DO BRASIL TECNO (5/6) R$ 273,14
            Próximas faturas
            21/05 PARC SALDO TOT - R DO BRASIL TECNO (6/6) R$ 273,14
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        destino.add(item("Lançamento da fatura", new BigDecimal("273.14"), null, null, null));

        InterFaturaTextoExtrator.complementar(destino, textoPago, 2026);

        assertEquals(1, destino.size());
        assertTrue(destino.get(0).getDescricao().contains("PARC SALDO"));
        assertEquals(5, destino.get(0).getParcelaAtual());
        assertEquals(6, destino.get(0).getTotalParcelas());
        assertTrue(FaturaPdfLayoutSupport.pareceListaGenericaIa(
            List.of(item("Lançamento da fatura", new BigDecimal("273.14"), null, null, null))
        ));
    }

    @Test
    void finalizarSubstituiListaGenericaQuandoTotalZerado() {
        String textoPago = """
            Banco Inter
            Valor da fatura R$ 0,00
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            21/05 PARC SALDO TOT - R DO BRASIL TECNO (5/6) R$ 273,14
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        destino.add(item("Lançamento da fatura", new BigDecimal("273.14"), null, null, null));

        InterFaturaTextoExtrator.finalizarListaInter(destino, textoPago, BigDecimal.ZERO, 2026);

        assertEquals(1, destino.size());
        assertTrue(destino.get(0).getDescricao().contains("PARC SALDO"));
        assertEquals(new BigDecimal("273.14"), destino.get(0).getValor());
    }

    @Test
    void complementarRemoveGenericosDaIaEMisturaEncargosSimulados() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 1.606,69
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            21/05 PARC SALDO TOT - R DO BRASIL TECNO (5/6) R$ 273,14
            28/04 APPLE.COM/BILL R$ 11,50
            Opções de pagamento
            Encargos Máximo Próximo Período 19,00% am R$ 0,19
            Juros de mora 1,00% am R$ 0,01
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        destino.add(item("Lançamento da fatura", new BigDecimal("585.69"), LocalDate.of(2026, 6, 25), null, null));
        destino.add(item("Lançamento da fatura", new BigDecimal("273.14"), LocalDate.of(2026, 6, 25), null, null));
        destino.add(item("Encargos Máximo Próximo Período 19,00% am", new BigDecimal("0.19"), LocalDate.of(2026, 6, 25), null, null));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("11.50"), LocalDate.of(2026, 4, 28), null, null));

        InterFaturaTextoExtrator.complementar(destino, texto, 2026);

        assertFalse(destino.stream().anyMatch(i -> i.getDescricao().contains("Lançamento da fatura")));
        assertFalse(destino.stream().anyMatch(i -> i.getDescricao().contains("Encargos Máximo")));
        assertTrue(destino.stream().anyMatch(i -> i.getDescricao().contains("PARC SALDO")));
        assertTrue(destino.stream().anyMatch(i -> i.getDescricao().contains("APPLE")));
    }

    @Test
    void ignoraLinhasSimulacaoTaxaAm() {
        assertTrue(InterFaturaTextoExtrator.pareceLinhaSimulacaoTaxaInter(
            "Juros de mora 1,00% am", new BigDecimal("0.01")));
        assertTrue(InterFaturaTextoExtrator.pareceLinhaSimulacaoTaxaInter(
            "IOF Internacional 3,50% am", new BigDecimal("0.04")));
        String texto = """
            Banco Inter
            Valor da fatura R$ 100,00
            Detalhamento da fatura
            12/05 MERCADO LIVRE R$ 100,00
            Juros de mora 1,00% am R$ 0,01
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals("MERCADO LIVRE", itens.get(0).getDescricao());
    }

    @Test
    void fallbackSubtotalDespesasDoMesQuandoFaturaPagaSemDetalhe() {
        String texto = """
            Banco Inter
            Valor da fatura R$ 0,00
            Fatura paga
            Data de vencimento 02/07/2026
            Data de corte: 25/06/2026
            Detalhamento da fatura
            R $ 0 , 0 0 DESPESAS DO MÊS R$ 657,58
            Despesas da fatura CARTÃO 2306 Data Movimentação Beneficiário PAGAMENTO ON LINE R$ 33,89
            Próximas faturas
            """;
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertEquals(1, itens.size());
        assertEquals(new BigDecimal("657.58"), itens.get(0).getValor());
        assertEquals("Despesas do cartão no período", itens.get(0).getDescricao());
    }

    @Test
    void extraiSubtotalDespesasDoMesComAcento() {
        assertEquals(
            new BigDecimal("657.58"),
            InterFaturaTextoExtrator.extrairSubtotalDespesasDoMes(
                "Resumo\nDESPESAS DO MÊS\n657,58\n"
            ).orElseThrow()
        );
    }

    private static ImportacaoFaturaItemDTO item(
        String desc,
        BigDecimal valor,
        LocalDate data,
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
