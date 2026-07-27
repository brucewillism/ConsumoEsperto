package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutSupport;
import com.consumoesperto.service.fatura.layout.InterFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.InterFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.ItauFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.ItauFaturaTextoExtrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Diagnóstico pontual com PDFs reais em Downloads (não falha CI se ausentes).
 */
class FaturaPdfRealLayoutProbeTest {

    private static final Path INTER = Path.of(
        System.getProperty("user.home"), "Downloads", "Fatura_inter.pdf");
    private static final Path ITAU = Path.of(
        System.getProperty("user.home"), "Downloads", "Fatura_Itau_06-07-2026.pdf");

    static boolean pdfsDisponiveis() {
        return Files.isRegularFile(INTER) && Files.isRegularFile(ITAU);
    }

    @Test
    @EnabledIf("pdfsDisponiveis")
    void diagnosticoPdfsReais() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        FaturaPdfExtracaoDeterministicaService det = new FaturaPdfExtracaoDeterministicaService(mapper);
        PdfTextExtractionService pdf = new PdfTextExtractionService();

        probe("ITAU", ITAU, pdf, det, new ItauFaturaPdfLayoutStrategy(), null);
        probe("INTER", INTER, pdf, det, new InterFaturaPdfLayoutStrategy(), null);
    }

    private static void probe(
        String label,
        Path path,
        PdfTextExtractionService pdf,
        FaturaPdfExtracaoDeterministicaService det,
        FaturaPdfLayoutStrategy layout,
        String senha
    ) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String texto;
        try {
            texto = pdf.extrairTexto(bytes, senha);
        } catch (IllegalArgumentException e) {
            System.out.println("\n========== " + label + " ==========");
            System.out.println("Arquivo: " + path);
            System.out.println("ERRO extracao: " + e.getMessage());
            System.out.println("(PDF provavelmente protegido — envie a senha ou versao aberta para analise completa)");
            return;
        }
        System.out.println("\n========== " + label + " ==========");
        System.out.println("Arquivo: " + path);
        System.out.println("Bytes: " + bytes.length);
        System.out.println("Texto chars: " + texto.length());
        System.out.println("Layout: " + layout.layout());
        System.out.println("Reconhece layout: " + layout.reconhece(FaturaPdfLayoutSupport.norm(texto)));
        System.out.println("Parece fatura: " + FaturaPdfLayoutSupport.pareceFaturaCartao(FaturaPdfLayoutSupport.norm(texto)));
        System.out.println("Amostra texto (800 chars):\n" + texto.substring(0, Math.min(800, texto.length())));

        int ano = 2026;
        if (label.equals("INTER")) {
            Optional<BigDecimal> total = InterFaturaTextoExtrator.extrairTotalFatura(texto);
            var itens = InterFaturaTextoExtrator.extrairLancamentos(texto, ano);
            BigDecimal soma = itens.stream().map(ImportacaoFaturaItemDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            System.out.println("Total PDF: " + total.orElse(null));
            System.out.println("Lancamentos: " + itens.size() + " soma=" + soma);
            itens.stream().limit(15).forEach(i ->
                System.out.println("  - " + i.getData() + " | " + i.getDescricao() + " | " + i.getValor()));
            if (itens.size() > 15) {
                System.out.println("  ... +" + (itens.size() - 15) + " itens");
            }
        } else {
            Optional<BigDecimal> total = ItauFaturaTextoExtrator.extrairTotalFatura(texto);
            Optional<BigDecimal> minimo = ItauFaturaTextoExtrator.extrairPagamentoMinimo(texto);
            var itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, ano);
            BigDecimal soma = itens.stream().map(ImportacaoFaturaItemDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            System.out.println("Total PDF: " + total.orElse(null));
            System.out.println("Pagamento minimo: " + minimo.orElse(null));
            System.out.println("Lancamentos: " + itens.size() + " soma=" + soma);
            itens.stream().limit(15).forEach(i ->
                System.out.println("  - " + i.getData() + " | " + i.getDescricao() + " | " + i.getValor()
                    + (i.getParcelaAtual() != null ? " parc " + i.getParcelaAtual() + "/" + i.getTotalParcelas() : "")));
            if (itens.size() > 15) {
                System.out.println("  ... +" + (itens.size() - 15) + " itens");
            }
        }

        var json = det.extrair(texto, layout);
        System.out.println("JSON deterministico tipo=" + json.path("tipoDocumento").asText()
            + " banco=" + json.path("bancoCartao").asText()
            + " venc=" + json.path("dataVencimento").asText()
            + " total=" + json.path("valorTotal").asText()
            + " lancamentos=" + json.path("lancamentos").size());
    }
}
