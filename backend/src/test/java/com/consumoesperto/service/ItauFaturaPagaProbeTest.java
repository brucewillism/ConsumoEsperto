package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutSupport;
import com.consumoesperto.service.fatura.layout.ItauFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.ItauFaturaTextoExtrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fatura Itaú de setembro/2026 vinha com R$ 0,00 no app: o PDF é de conferência, com
 * «Total desta fatura 0,00» porque o saldo foi quitado em conta, e o ciclo vale R$ 1.556,08.
 * Requer o PDF em Downloads e a senha em {@code FATURA_PDF_SENHA}.
 */
class ItauFaturaPagaProbeTest {

    private static final Path PDF = Path.of(
        System.getProperty("user.home"), "Downloads", "Fatura_MASTERCARD_100481081587_08-09-2026.pdf");

    static boolean disponivel() {
        String senha = System.getenv("FATURA_PDF_SENHA");
        return senha != null && !senha.isBlank() && Files.isRegularFile(PDF);
    }

    @Test
    @EnabledIf("disponivel")
    void reconheceFaturaQuitadaNoBancoEPreservaValorDoCiclo() throws Exception {
        String texto = texto();
        Optional<BigDecimal> totalPdf = ItauFaturaTextoExtrator.extrairTotalFatura(texto);

        FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf situacao =
            FaturaPdfLayoutSupport.detectarSituacaoLeituraFatura(texto, totalPdf.orElse(null));

        List<ImportacaoFaturaItemDTO> itens = extrair(texto);
        BigDecimal soma = soma(itens);

        System.out.println("=== ITAU: totalPdf=" + totalPdf.orElse(null)
            + " situacao=" + situacao
            + " itens=" + itens.size()
            + " soma=" + soma);
        for (ImportacaoFaturaItemDTO i : itens) {
            System.out.printf("%s | %-45s | %10s | parcela=%s/%s%n",
                i.getData(), i.getDescricao(), i.getValor(), i.getParcelaAtual(), i.getTotalParcelas());
        }
        System.out.println("=== historico="
            + ItauFaturaTextoExtrator.extrairValorHistoricoFaturaPaga(texto).orElse(null));

        assertEquals(
            FaturaPdfLayoutSupport.SituacaoLeituraFaturaPdf.PAGA_NO_BANCO,
            situacao,
            "saldo zerado por quitação em conta deve ser lido como fatura já paga"
        );
        assertEquals(
            new BigDecimal("1556.08"),
            ItauFaturaTextoExtrator.extrairValorHistoricoFaturaPaga(texto).orElse(null),
            "«Total dos lançamentos atuais» é o valor efetivamente pago no ciclo"
        );
        // A soma fica R$ 22,00 acima porque o crédito «Redução Mensalidade» não entra como despesa,
        // por decisão de modelo. A diferença cabe na tolerância de 2% do checksum.
        assertEquals(new BigDecimal("1578.08"), soma);
        assertEquals(13, itens.size(), "11 compras do cartão + Pix parcelado + mensalidade");
    }

    @Test
    @EnabledIf("disponivel")
    void naoDescartaDespesasDistintasDeMesmoValorNoMesmoDia() throws Exception {
        List<ImportacaoFaturaItemDTO> doDia11 = extrair(texto()).stream()
            .filter(i -> i.getValor() != null && i.getValor().compareTo(new BigDecimal("53.20")) == 0)
            .toList();

        assertEquals(2, doDia11.size(), "«BAR E RESTAURANTE DA CR» e «RESTAURANTE DO BIURECIF» em 11/08");
    }

    private static String texto() throws Exception {
        return new PdfTextExtractionService()
            .extrairTexto(Files.readAllBytes(PDF), System.getenv("FATURA_PDF_SENHA"));
    }

    private static List<ImportacaoFaturaItemDTO> extrair(String texto) {
        List<ImportacaoFaturaItemDTO> itens = ItauFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        return new ItauFaturaPdfLayoutStrategy().sanitizarLancamentos(itens);
    }

    private static BigDecimal soma(List<ImportacaoFaturaItemDTO> itens) {
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
