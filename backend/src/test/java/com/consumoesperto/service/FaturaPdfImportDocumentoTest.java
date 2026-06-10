package com.consumoesperto.service;

import com.consumoesperto.service.fatura.layout.BancoFaturaLayout;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutSupport;
import com.consumoesperto.service.fatura.layout.ItauFaturaPdfLayoutStrategy;
import com.consumoesperto.util.BancoBrasilCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaturaPdfImportDocumentoTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ItauFaturaPdfLayoutStrategy itauLayout = new ItauFaturaPdfLayoutStrategy();

    @Test
    void aceitaExtratoContaComSinaisDeFatura() throws Exception {
        String json = """
            {
              "tipoDocumento": "EXTRATO_CONTA",
              "bancoCartao": "Itaú",
              "dataVencimento": "2026-06-02",
              "dataFechamento": "2026-05-26",
              "valorTotal": 1500.00,
              "pagamentoMinimo": 150.00,
              "lancamentos": [{"data":"2026-05-10","descricao":"Mercado","valor":50.00}]
            }
            """;
        assertTrue(FaturaPdfImportService.temSinaisEstruturaisFaturaCartao(mapper.readTree(json)));
    }

    @Test
    void rejeitaDocumentoSemLancamentos() throws Exception {
        String json = """
            {
              "tipoDocumento": "EXTRATO_CONTA",
              "bancoCartao": "Itaú",
              "dataVencimento": "2026-06-02",
              "valorTotal": 1500.00,
              "pagamentoMinimo": 150.00,
              "lancamentos": []
            }
            """;
        assertFalse(FaturaPdfImportService.temSinaisEstruturaisFaturaCartao(mapper.readTree(json)));
    }

    @Test
    void aceitaPorLayoutItauQuandoTextoPareceFatura() {
        String texto = "Itaú Unibanco fatura cartão data de vencimento pagamento mínimo lançamentos no cartão";
        String norm = FaturaPdfLayoutSupport.norm(texto);
        assertTrue(itauLayout.reconhece(norm));
        assertTrue(FaturaPdfLayoutSupport.pareceFaturaCartao(norm));
        assertEqualsLayout(BancoFaturaLayout.ITAU, itauLayout.layout());
    }

    @Test
    void placeholderDaIaNaoContaComoBancoUtil() {
        assertFalse(FaturaPdfLayoutSupport.bancoExtraidoUtil("..."));
        assertFalse(FaturaPdfLayoutSupport.bancoExtraidoUtil("Mastercard"));
        assertTrue(FaturaPdfLayoutSupport.bancoExtraidoUtil("Itaú"));
    }

    @Test
    void infereItauDoTextoQuandoIaRetornaPlaceholder() {
        String texto = "Itaú Unibanco demonstrativo total desta fatura vencimento";
        assertEquals("Itaú", FaturaPdfLayoutSupport.inferirBancoEmissorDoTexto(FaturaPdfLayoutSupport.norm(texto)));
        assertEquals("Itaú", itauLayout.sugerirBancoCartao(FaturaPdfLayoutSupport.norm(texto), "..."));
    }

    @Test
    void cartaoItauAzulCorrespondeAoEmissorItau() {
        assertTrue(BancoBrasilCatalog.bancosCorrespondem("itau azul", "Itaú"));
        assertTrue(BancoBrasilCatalog.bancosCorrespondem("itau", "Itaú"));
    }

    private static void assertEqualsLayout(BancoFaturaLayout expected, BancoFaturaLayout actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
