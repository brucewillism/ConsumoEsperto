package com.consumoesperto.service;

import com.consumoesperto.service.fatura.layout.InterFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.ItauFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.MercadoPagoFaturaPdfLayoutStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaturaPdfExtracaoDeterministicaServiceTest {

    private FaturaPdfExtracaoDeterministicaService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new FaturaPdfExtracaoDeterministicaService(mapper);
    }

    @Test
    void suportaQualquerLayoutConhecido() {
        for (com.consumoesperto.service.fatura.layout.BancoFaturaLayout layout
            : com.consumoesperto.service.fatura.layout.BancoFaturaLayout.values()) {
            assertTrue(FaturaPdfExtracaoDeterministicaService.suporta(layout));
        }
        assertFalse(FaturaPdfExtracaoDeterministicaService.suporta(null));
    }

    @Test
    void extraiBradescoSemIa() {
        String texto = """
            Bradesco
            Lançamentos
            01/04 MERCADO XYZ R$ 80,00
            Total da fatura R$ 80,00
            Data de vencimento 05/06/2026
            """;
        JsonNode json = service.extrair(texto, new com.consumoesperto.service.fatura.layout.BradescoFaturaPdfLayoutStrategy());
        assertEquals("FATURA_CARTAO", json.path("tipoDocumento").asText());
        assertEquals(1, json.path("lancamentos").size());
    }

    @Test
    void extraiBancoBrasilSemIa() {
        String texto = """
            Banco do Brasil
            Lançamentos no cartão
            05/05 POSTO BR R$ 200,00
            Total da fatura R$ 200,00
            Data de vencimento 10/06/2026
            """;
        JsonNode json = service.extrair(texto, new com.consumoesperto.service.fatura.layout.BancoBrasilFaturaPdfLayoutStrategy());
        assertEquals(1, json.path("lancamentos").size());
    }

    @Test
    void extraiItauSemIa() {
        String texto = """
            Itaú Unibanco
            Fatura do cartão itau azul
            Data de vencimento 02/06/2026
            LANÇAMENTOS: compras e saques
            05/05 MERCADO CENTRAL 45,90
            10/05 POSTO IPIRANGA 02/03 120,00
            Total desta fatura R$ 165,90
            Pagamento mínimo R$ 25,00
            """;
        JsonNode json = service.extrair(texto, new ItauFaturaPdfLayoutStrategy());
        assertEquals("FATURA_CARTAO", json.path("tipoDocumento").asText());
        assertEquals("Itaú", json.path("bancoCartao").asText());
        assertEquals("2026-06-02", json.path("dataVencimento").asText());
        assertEquals(2, json.path("lancamentos").size());
        assertEquals(0, new BigDecimal("165.90").compareTo(new BigDecimal(json.path("valorTotal").asText())));
        assertEquals(0, new BigDecimal("25.00").compareTo(new BigDecimal(json.path("pagamentoMinimo").asText())));
    }

    @Test
    void extraiInterSemIa() {
        String texto = """
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
            Próximas faturas
            Opções de pagamento
            """;
        JsonNode json = service.extrair(texto, new InterFaturaPdfLayoutStrategy());
        assertEquals("FATURA_CARTAO", json.path("tipoDocumento").asText());
        assertTrue(json.path("lancamentos").size() >= 2);
        assertEquals("2026-05-25", json.path("dataFechamento").asText());
    }

    @Test
    void extraiMercadoPagoSemIa() {
        String texto = """
            Mercado Pago
            Movimentações na fatura
            10/05 LOJA EXEMPLO R$ 120,50
            12/05 ASSINATURA STREAM R$ 29,90
            Resumo da fatura
            Total da fatura R$ 150,40
            Data de vencimento 15/06/2026
            """;
        JsonNode json = service.extrair(texto, new MercadoPagoFaturaPdfLayoutStrategy());
        assertEquals(2, json.path("lancamentos").size());
        assertEquals(0, new BigDecimal("150.40").compareTo(new BigDecimal(json.path("valorTotal").asText())));
    }
}
