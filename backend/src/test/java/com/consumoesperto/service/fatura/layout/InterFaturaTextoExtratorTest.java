package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        23/04 APPLE.COM/BILL R$ 11,50
        29/04 APPLE.COM/BILL R$ 18,00
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
        BigDecimal soma = itens.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("291.14"), soma);
        assertTrue(itens.stream().anyMatch(i -> i.getDescricao().contains("PARC SALDO")));
        assertEquals(4, itens.stream().filter(i -> i.getDescricao().contains("PARC")).findFirst().orElseThrow().getParcelaAtual());
    }

    @Test
    void complementarSubstituiListaInfladaDaIa() {
        List<ImportacaoFaturaItemDTO> destino = new ArrayList<>();
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), 4, 6));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("11.50"), null, null));
        destino.add(item("APPLE.COM/BILL", new BigDecimal("18.00"), null, null));
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), 5, 6));
        destino.add(item("PARC SALDO TOT", new BigDecimal("273.14"), 6, 6));
        destino.add(item("Parcelamento 1+5x", new BigDecimal("71.81"), null, null));
        destino.add(item("IOF simulacao", new BigDecimal("12.00"), null, null));

        InterFaturaTextoExtrator.complementar(destino, TEXTO, 2026);
        BigDecimal soma = destino.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("291.14"), soma);
        assertEquals(3, destino.size());
        assertFalse(destino.stream().anyMatch(i -> i.getValor().compareTo(new BigDecimal("71.81")) == 0));
    }

    @Test
    void ignoraSimulacaoParcelamento() {
        assertTrue(InterFaturaTextoExtrator.deveIgnorarDescricao("1 + 5x R$ 71,81"));
        assertTrue(InterFaturaTextoExtrator.deveIgnorarDescricao("Opções de pagamento"));
    }

    private static ImportacaoFaturaItemDTO item(
        String desc,
        BigDecimal valor,
        Integer parcela,
        Integer total
    ) {
        ImportacaoFaturaItemDTO i = new ImportacaoFaturaItemDTO();
        i.setDescricao(desc);
        i.setValor(valor);
        i.setParcelaAtual(parcela);
        i.setTotalParcelas(total);
        return i;
    }
}
