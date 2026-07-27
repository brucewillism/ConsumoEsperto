package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.NubankFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.NubankFaturaTextoExtrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("pdfDisponivel")
class NubankFaturaAgostoProbeTest {

    private static final Path PDF = Path.of(
        System.getProperty("user.home"), "Downloads", "Nubank_2026-08-02.pdf");

    static boolean pdfDisponivel() {
        return Files.isRegularFile(PDF);
    }

    @Test
    void extraiTotalComprasELancamentosUnicosDoPdfAgosto2026() throws Exception {
        byte[] bytes = Files.readAllBytes(PDF);
        PdfTextExtractionService pdf = new PdfTextExtractionService();
        String texto = pdf.extrairTexto(bytes);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        FaturaPdfExtracaoDeterministicaService det = new FaturaPdfExtracaoDeterministicaService(mapper);

        int ano = 2026;
        BigDecimal totalCompras = NubankFaturaTextoExtrator.extrairTotalCompras(texto).orElseThrow();
        BigDecimal totalAPagar = NubankFaturaTextoExtrator.extrairTotalAPagar(texto).orElseThrow();
        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(texto, ano);
        NubankFaturaTextoExtrator.finalizarLista(itens, texto, totalCompras);
        List<ImportacaoFaturaItemDTO> sanitizados = new NubankFaturaPdfLayoutStrategy().sanitizarLancamentos(itens);
        BigDecimal soma = sanitizados.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        JsonNode json = det.extrair(texto, new NubankFaturaPdfLayoutStrategy());

        assertEquals(new BigDecimal("4038.47"), totalCompras);
        assertEquals(new BigDecimal("2274.23"), totalAPagar);
        assertEquals(new BigDecimal("4038.47"), json.path("valorTotal").decimalValue());
        assertTrue(sanitizados.size() >= 50 && sanitizados.size() <= 65,
            "esperado ~57 lançamentos, obteve " + sanitizados.size());
        assertTrue(soma.subtract(totalCompras).abs().compareTo(new BigDecimal("280.00")) <= 0,
            "soma " + soma + " deve estar próxima do total de compras " + totalCompras);
    }
}
