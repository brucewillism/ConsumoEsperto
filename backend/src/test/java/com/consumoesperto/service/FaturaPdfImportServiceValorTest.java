package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaturaPdfImportServiceValorTest {

    @Test
    void usaValorDaImportacaoQuandoPositivo() {
        ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
        item.setValor(new BigDecimal("100.00"));
        BigDecimal total = FaturaPdfImportService.resolverValorTotalParaFatura(
            new BigDecimal("250.50"),
            List.of(item)
        );
        assertEquals(new BigDecimal("250.50"), total);
    }

    @Test
    void usaSomaDosItensQuandoTotalPdfZerado() {
        ImportacaoFaturaItemDTO a = new ImportacaoFaturaItemDTO();
        a.setValor(new BigDecimal("55.58"));
        ImportacaoFaturaItemDTO b = new ImportacaoFaturaItemDTO();
        b.setValor(new BigDecimal("120.42"));
        BigDecimal total = FaturaPdfImportService.resolverValorTotalParaFatura(
            BigDecimal.ZERO,
            List.of(a, b)
        );
        assertEquals(new BigDecimal("176.00"), total);
    }

    @Test
    void retornaZeroSemItensETotalZerado() {
        assertEquals(BigDecimal.ZERO.setScale(2), FaturaPdfImportService.resolverValorTotalParaFatura(null, List.of()));
    }
}
