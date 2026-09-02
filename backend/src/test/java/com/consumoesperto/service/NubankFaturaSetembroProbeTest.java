package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.service.fatura.layout.NubankFaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.NubankFaturaTextoExtrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fatura de setembro/2026 divergia R$ 154,74 do total de compras por três defeitos somados:
 * dedupe que descartava compras recorrentes em datas distintas, rodapé de paginação que apagava o
 * último lançamento de cada página e Pix parcelado lançado pelo financiamento inteiro.
 */
@EnabledIf("pdfDisponivel")
class NubankFaturaSetembroProbeTest {

    private static final Path PDF = Path.of(
        System.getProperty("user.home"), "Downloads", "Nubank_2026-09-02.pdf");

    static boolean pdfDisponivel() {
        return Files.isRegularFile(PDF);
    }

    @Test
    void somaDosLancamentosBateComTotalDeComprasDoPdfSetembro2026() throws Exception {
        List<ImportacaoFaturaItemDTO> itens = extrair();

        BigDecimal totalCompras = NubankFaturaTextoExtrator.extrairTotalCompras(texto()).orElseThrow();

        assertEquals(new BigDecimal("3867.37"), totalCompras);
        assertEquals(totalCompras, soma(itens), "soma dos lançamentos deve fechar com o total de compras");
        assertEquals(62, itens.size(), "61 transações do cartão + 1 Pix parcelado");
    }

    @Test
    void mantemComprasRecorrentesQueODedupeDescartava() throws Exception {
        List<ImportacaoFaturaItemDTO> itens = extrair();

        assertEquals(2, ocorrencias(itens, "Claro Flex", "44.99"), "08 AGO e 24 AGO");
        assertEquals(2, ocorrencias(itens, "Dl*Uberrides", "16.95"), "30 JUL e 25 AGO");
        assertEquals(1, ocorrencias(itens, "Uber Uber", "16.94"), "difere 1 centavo do lançamento de 28 JUL");
    }

    @Test
    void mantemUltimoLancamentoDePaginaEDeSecao() throws Exception {
        List<ImportacaoFaturaItemDTO> itens = extrair();

        assertEquals(1, ocorrencias(itens, "Restaurante e Pizzari", "16.00"), "fecha a página 6");
        assertEquals(1, ocorrencias(itens, "Quotidiano Cafe", "15.00"), "fecha o bloco de transações");
    }

    @Test
    void lancaPixParceladoPelaParcelaDoMesENaoPeloFinanciamento() throws Exception {
        List<ImportacaoFaturaItemDTO> itens = extrair();

        assertEquals(1, ocorrencias(itens, "PAMELA PRISCILA", "264.63"),
            "2 parcelas de R$ 264,63; o total financiado de R$ 529,25 não entra na fatura");
        assertEquals(0, ocorrencias(itens, "PAMELA PRISCILA", "529.25"));
    }

    private static String texto() throws Exception {
        return new PdfTextExtractionService().extrairTexto(Files.readAllBytes(PDF));
    }

    private static List<ImportacaoFaturaItemDTO> extrair() throws Exception {
        String texto = texto();
        BigDecimal totalCompras = NubankFaturaTextoExtrator.extrairTotalCompras(texto).orElseThrow();
        List<ImportacaoFaturaItemDTO> itens = NubankFaturaTextoExtrator.extrairLancamentos(texto, 2026);
        NubankFaturaTextoExtrator.finalizarLista(itens, texto, totalCompras);
        return new NubankFaturaPdfLayoutStrategy().sanitizarLancamentos(itens);
    }

    private static BigDecimal soma(List<ImportacaoFaturaItemDTO> itens) {
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long ocorrencias(List<ImportacaoFaturaItemDTO> itens, String descricao, String valor) {
        return itens.stream()
            .filter(i -> i.getDescricao() != null
                && i.getDescricao().toLowerCase().contains(descricao.toLowerCase()))
            .filter(i -> i.getValor() != null && i.getValor().compareTo(new BigDecimal(valor)) == 0)
            .count();
    }
}
