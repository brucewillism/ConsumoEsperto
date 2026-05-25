package com.consumoesperto.util;

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
    void detectaBbQuandoTotalESomaDeAnteriorEAtual() throws Exception {
        ObjectNode n = mapper.createObjectNode();
        n.put("saldoFaturaAnterior", 500.00);
        n.put("saldoFaturaAtual", 300.00);
        n.put("valorTotal", 800.00);

        Optional<SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbPendente> r =
            SaldoAnteriorFaturaBbSupport.detectar(n, "bb", new BigDecimal("800.00"), new BigDecimal("300.00"), Optional.empty());

        assertTrue(r.isPresent());
        assertEquals(new BigDecimal("500.00"), r.get().saldoAnterior());
        assertEquals(new BigDecimal("300.00"), r.get().saldoAtual());
    }

    @Test
    void valorTotalAposEscolhaSomarOuSoAtual() {
        SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbMeta meta =
            new SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbMeta(
                new BigDecimal("500.00"),
                new BigDecimal("300.00"),
                new BigDecimal("800.00"),
                false,
                false
            );
        assertEquals(new BigDecimal("800.00"), SaldoAnteriorFaturaBbSupport.valorTotalAposEscolha(meta, true));
        assertEquals(new BigDecimal("300.00"), SaldoAnteriorFaturaBbSupport.valorTotalAposEscolha(meta, false));
    }

    @Test
    void pendenteNaoResolvidoAteMarcarMeta() {
        List<String> auditorias = new ArrayList<>();
        SaldoAnteriorFaturaBbSupport.registrarPendenciaNasAuditorias(
            auditorias,
            new SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbPendente(
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("150.00")
            )
        );
        assertTrue(SaldoAnteriorFaturaBbSupport.pendenteNaoResolvido(auditorias));
        List<String> resolvido = SaldoAnteriorFaturaBbSupport.marcarMetaResolvida(auditorias, false);
        assertFalse(SaldoAnteriorFaturaBbSupport.pendenteNaoResolvido(resolvido));
    }
}
