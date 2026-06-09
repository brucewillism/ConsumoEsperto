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
