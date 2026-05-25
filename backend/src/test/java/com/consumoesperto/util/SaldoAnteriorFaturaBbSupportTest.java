package com.consumoesperto.util;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaldoAnteriorFaturaBbSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void detectaCasoRealBbSomaLancamentosIncluiSaldoAnterior() throws Exception {
        ObjectNode n = mapper.createObjectNode();
        n.put("saldoFaturaAnterior", 411.28);
        n.put("valorTotal", 441.66);

        List<ImportacaoFaturaItemDTO> itens = List.of(
            item("SALDO FATURA ANTERIOR", "411.28"),
            item("COMPRA LOJA", "30.38"),
            item("OUTRA COMPRA", "411.28")
        );
        BigDecimal somaTodos = new BigDecimal("852.94");

        Optional<SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbPendente> r =
            SaldoAnteriorFaturaBbSupport.detectar(n, "Banco do Brasil", new BigDecimal("441.66"), somaTodos, itens, Optional.empty());

        assertTrue(r.isPresent());
        assertEquals(new BigDecimal("411.28"), r.get().saldoAnterior());
        assertEquals(new BigDecimal("441.66"), r.get().saldoMesAtual());
    }

    @Test
    void naoSomarUsaSaldoMesAtualNaoSomaBruta() {
        SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbMeta meta =
            new SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbMeta(
                new BigDecimal("411.28"),
                new BigDecimal("441.66"),
                new BigDecimal("441.66"),
                new BigDecimal("852.94"),
                false,
                false
            );
        assertEquals(new BigDecimal("852.94"), SaldoAnteriorFaturaBbSupport.valorTotalAposEscolha(meta, true));
        assertEquals(new BigDecimal("441.66"), SaldoAnteriorFaturaBbSupport.valorTotalAposEscolha(meta, false));
    }

    @Test
    void desmarcaLinhaSaldoFaturaAnterior() {
        ImportacaoFaturaItemDTO saldo = item("SALDO FATURA ANTERIOR", "411.28");
        saldo.setNovo(true);
        saldo.setSelecionado(true);
        List<ImportacaoFaturaItemDTO> itens = new ArrayList<>(List.of(saldo, item("PADARIA", "10.00")));
        assertEquals(1, SaldoAnteriorFaturaBbSupport.desmarcarLinhasSaldoAnterior(itens));
        assertFalse(saldo.isSelecionado());
        assertFalse(saldo.isNovo());
    }

    @Test
    void ehAuditoriaDivergenciaComAcento() {
        assertTrue(SaldoAnteriorFaturaBbSupport.ehAuditoriaDivergenciaChecksum(
            "Atenção: a soma dos lançamentos extraídos (852.94) não bate com o total da fatura (441.66)."));
    }

    private static ImportacaoFaturaItemDTO item(String desc, String valor) {
        ImportacaoFaturaItemDTO i = new ImportacaoFaturaItemDTO();
        i.setDescricao(desc);
        i.setValor(new BigDecimal(valor));
        return i;
    }
}
