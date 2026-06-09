package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica sanitização de lançamentos Nubank (Pix duplicado e subtotal de portador).
 */
class FaturaPdfImportNubankSanitizerTest {

    @Test
    void removeComponentesPixQuandoHaTotalAPagarNaMesmaData() {
        LocalDate dia = LocalDate.of(2026, 5, 8);
        List<ImportacaoFaturaItemDTO> itens = new ArrayList<>();
        itens.add(item(dia, "valor da transacao de R$ 284,92", "284.92"));
        itens.add(item(dia, "IOF de R$ 1,68", "1.68"));
        itens.add(item(dia, "juros de R$ 20,60", "20.60"));
        itens.add(item(dia, "PREFEITURA DE CAMARAGIBE Total a pagar R$ 307,20", "307.20"));
        itens.add(item(LocalDate.of(2026, 4, 25), "A B Vilela Silva - Parcela 6/10", "52.00"));

        List<ImportacaoFaturaItemDTO> out = FaturaPdfImportService.sanitizarLancamentosExtraidosParaTeste(itens);

        assertEquals(2, out.size());
        assertEquals(new BigDecimal("307.20"), out.get(0).getValor());
        assertEquals(new BigDecimal("52.00"), out.get(1).getValor());
    }

    @Test
    void removeSubtotalPortadorSemCartao() {
        List<ImportacaoFaturaItemDTO> itens = List.of(
            item(null, "Bruce W M Silva", "2425.51"),
            item(LocalDate.of(2026, 4, 26), "Atacadao 150 As", "90.53")
        );

        List<ImportacaoFaturaItemDTO> out = FaturaPdfImportService.sanitizarLancamentosExtraidosParaTeste(itens);

        assertEquals(1, out.size());
        assertEquals("Atacadao 150 As", out.get(0).getDescricao());
    }

    private static ImportacaoFaturaItemDTO item(LocalDate data, String desc, String valor) {
        ImportacaoFaturaItemDTO dto = new ImportacaoFaturaItemDTO();
        dto.setData(data);
        dto.setDescricao(desc);
        dto.setValor(new BigDecimal(valor));
        dto.setNovo(true);
        dto.setSelecionado(true);
        return dto;
    }
}
