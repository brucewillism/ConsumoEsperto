package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extrator determinístico genérico para faturas de cartão sem layout específico reconhecido.
 */
public final class GenericoFaturaTextoExtrator {

    private static final FaturaTextoExtratorPadrao.BancoTextoConfig CFG = new FaturaTextoExtratorPadrao.BancoTextoConfig(
        "Genérico",
        new String[] {
            "lancamentos",
            "lançamentos",
            "transacoes",
            "transações",
            "transacoes de",
            "transações de",
            "movimentacao do cartao",
            "movimentação do cartão",
            "movimentacoes na fatura",
            "movimentações na fatura",
            "demonstrativo de fatura",
            "detalhamento da fatura",
            "detalhes de consumo",
            "compras e saques",
            "despesas da fatura"
        },
        concat(
            FaturaTextoExtratorPadrao.fimProximasFaturas(),
            "simulacao de parcelamento",
            "simulação de parcelamento",
            "limite de credito",
            "limite de crédito",
            "programa de recompensa",
            "pontos ",
            "cet "
        ),
        new Pattern[] {
            FaturaTextoExtratorPadrao.padraoTotalPadrao(),
            Pattern.compile("(?i)total\\s+a\\s+pagar[^\\d]{0,80}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"),
            Pattern.compile("(?i)total desta fatura[^\\d]{0,80}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})")
        },
        new String[0],
        FaturaTextoExtratorPadrao::pareceEncargoComum,
        d -> false
    );

    private GenericoFaturaTextoExtrator() {
    }

    public static List<ImportacaoFaturaItemDTO> extrairLancamentos(String textoPdf, int anoReferencia) {
        return FaturaTextoExtratorPadrao.extrairLancamentos(textoPdf, anoReferencia, CFG);
    }

    public static Optional<BigDecimal> extrairTotalFatura(String textoPdf) {
        return FaturaTextoExtratorPadrao.extrairTotalFatura(textoPdf, CFG);
    }

    public static void complementar(List<ImportacaoFaturaItemDTO> destino, String textoPdf, int anoReferencia) {
        FaturaTextoExtratorPadrao.complementar(destino, textoPdf, anoReferencia, CFG);
    }

    public static void finalizarLista(
        List<ImportacaoFaturaItemDTO> itens,
        String textoPdf,
        BigDecimal totalFatura,
        int anoReferencia
    ) {
        FaturaTextoExtratorPadrao.finalizar(itens, textoPdf, totalFatura, anoReferencia, CFG);
    }

    private static String[] concat(String[] base, String... extra) {
        String[] out = new String[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
