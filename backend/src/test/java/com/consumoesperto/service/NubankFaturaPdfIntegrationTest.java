package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.NubankFaturaTextoExtrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integração opcional com o PDF real do usuário (Downloads).
 */
class NubankFaturaPdfIntegrationTest {

    private static final Path PDF = Path.of(
        System.getProperty("user.home"),
        "Downloads",
        "Nubank_2026-06-02.pdf"
    );

    static boolean pdfDisponivel() {
        return Files.isRegularFile(PDF);
    }

    @Test
    @EnabledIf("pdfDisponivel")
    void extraiCartaoEPixDoPdfReal() throws Exception {
        byte[] bytes = Files.readAllBytes(PDF);
        PdfTextExtractionService extractor = new PdfTextExtractionService();
        String texto = extractor.extrairTexto(bytes);

        var itens = NubankFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        long pix = itens.stream().filter(i ->
            desc(i).contains("pix/boleto")
                || desc(i).contains("prefeitura")
                || desc(i).contains("pamela")
                || desc(i).contains("pay2m")
                || desc(i).contains("eduardo")).count();

        BigDecimal soma = itens.stream()
            .map(ImportacaoFaturaItemDTO::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(itens.size() >= 29, "esperado ~30 lançamentos, obteve " + itens.size());
        assertTrue(pix >= 5, "esperado 5 Pix financiados, obteve " + pix);
        assertTrue(soma.compareTo(new BigDecimal("2840")) >= 0, "soma muito baixa: " + soma);
        assertTrue(soma.compareTo(new BigDecimal("2900")) <= 0, "soma muito alta: " + soma);
    }

    private static String desc(ImportacaoFaturaItemDTO i) {
        return i.getDescricao() != null ? i.getDescricao().toLowerCase() : "";
    }

    private static String norm(ImportacaoFaturaItemDTO i) {
        return desc(i);
    }
}
