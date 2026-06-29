package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutSupport;
import com.consumoesperto.service.fatura.layout.InterFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.ItauFaturaTextoExtrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integração opcional com PDFs reais protegidos (Downloads).
 * Defina {@code FATURA_PDF_SENHA} (5 ou 6 primeiros dígitos do CPF) para executar.
 */
class FaturaPdfProtegidoIntegrationTest {

    private static final Path INTER = Path.of(
        System.getProperty("user.home"), "Downloads", "Fatura_inter.pdf");
    private static final Path ITAU = Path.of(
        System.getProperty("user.home"), "Downloads", "Fatura_Itau_06-07-2026.pdf");

    static boolean senhaDisponivel() {
        String s = System.getenv("FATURA_PDF_SENHA");
        return s != null && !s.isBlank();
    }

    static boolean interDisponivel() {
        return senhaDisponivel() && Files.isRegularFile(INTER);
    }

    static boolean itauDisponivel() {
        return senhaDisponivel() && Files.isRegularFile(ITAU);
    }

    @Test
    @EnabledIf("interDisponivel")
    void extraiLancamentosInterPdfReal() throws Exception {
        byte[] bytes = Files.readAllBytes(INTER);
        PdfTextExtractionService extractor = new PdfTextExtractionService();
        String texto = extractor.extrairTexto(bytes, System.getenv("FATURA_PDF_SENHA"));
        List<ImportacaoFaturaItemDTO> itens = InterFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertFalse(itens.isEmpty(), "Inter: nenhum lançamento extraído — texto len=" + texto.length());
        assertFalse(
            FaturaPdfLayoutSupport.pareceListaGenericaIa(itens),
            "Inter: lista ainda genérica"
        );
    }

    @Test
    @EnabledIf("itauDisponivel")
    void extraiLancamentosItauPdfReal() throws Exception {
        byte[] bytes = Files.readAllBytes(ITAU);
        PdfTextExtractionService extractor = new PdfTextExtractionService();
        String texto = extractor.extrairTexto(bytes, System.getenv("FATURA_PDF_SENHA"));
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        assertFalse(itens.isEmpty(), "Itaú: nenhum lançamento extraído — texto len=" + texto.length());
        assertTrue(
            itens.stream().anyMatch(i -> i.getDescricao() != null && i.getDescricao().length() > 8),
            "Itaú: descrições ainda genéricas"
        );
    }
}
