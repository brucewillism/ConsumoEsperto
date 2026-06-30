package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.InterFaturaTextoExtrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integração com Fatura_inter.pdf real (Downloads).
 * Defina {@code FATURA_PDF_SENHA} (6 primeiros dígitos do CPF) para executar.
 */
class InterPdfRealExtracaoTest {

    private static final Path INTER = Path.of(
        System.getProperty("user.home"), "Downloads", "Fatura_inter.pdf");

    static boolean disponivel() {
        String s = System.getenv("FATURA_PDF_SENHA");
        return s != null && !s.isBlank() && Files.isRegularFile(INTER);
    }

    @Test
    @EnabledIf("disponivel")
    void extraiAoMenosUmLancamentoDoPdfReal() throws Exception {
        byte[] bytes = Files.readAllBytes(INTER);
        PdfTextExtractionService extractor = new PdfTextExtractionService();
        String texto = extractor.extrairTexto(bytes, System.getenv("FATURA_PDF_SENHA"));
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertFalse(itens.isEmpty(), "Inter PDF real: 0 lançamentos — texto len=" + texto.length());
    }
}
