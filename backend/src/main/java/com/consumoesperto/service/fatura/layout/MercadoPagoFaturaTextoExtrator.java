package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MercadoPagoFaturaTextoExtrator {

    private static final FaturaTextoExtratorPadrao.BancoTextoConfig CFG = new FaturaTextoExtratorPadrao.BancoTextoConfig(
        "Mercado Pago",
        new String[] { "movimentacoes na fatura", "movimentações na fatura", "detalhamento da fatura" },
        concat(FaturaTextoExtratorPadrao.fimProximasFaturas(),
            "resumo da fatura", "consumos de", "tarifas e encargos", "total da fatura de",
            "pagamentos e creditos devolvidos", "pagamentos e créditos devolvidos"),
        new Pattern[] {
            FaturaTextoExtratorPadrao.padraoTotalPadrao(),
            Pattern.compile("(?i)total da fatura de[^\\d]{0,60}(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})")
        },
        new String[] { "credit card mp", "cartao mercado pago" },
        FaturaTextoExtratorPadrao::pareceEncargoComum,
        d -> false
    );

    private MercadoPagoFaturaTextoExtrator() {
    }

    static boolean deveIgnorarDescricao(String descricao) {
        return FaturaTextoExtratorPadrao.deveIgnorar(descricao, CFG);
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
