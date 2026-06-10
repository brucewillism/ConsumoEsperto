package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class BancoBrasilFaturaTextoExtrator {

    private static final FaturaTextoExtratorPadrao.BancoTextoConfig CFG = new FaturaTextoExtratorPadrao.BancoTextoConfig(
        "Banco do Brasil",
        new String[] { "lancamentos no cartao", "lançamentos no cartão", "lancamentos do cartao", "demonstrativo de fatura" },
        concat(FaturaTextoExtratorPadrao.fimProximasFaturas(),
            "total da fatura", "opcoes de parcelamento", "opções de parcelamento", "limite de credito"),
        new java.util.regex.Pattern[] { FaturaTextoExtratorPadrao.padraoTotalPadrao() },
        new String[] { "pagamento recebido", "saldo restante" },
        FaturaTextoExtratorPadrao::pareceEncargoComum,
        BancoBrasilFaturaTextoExtrator::pareceSaldoAnterior
    );

    private BancoBrasilFaturaTextoExtrator() {
    }

    static boolean deveIgnorarDescricao(String descricao) {
        return FaturaTextoExtratorPadrao.deveIgnorar(descricao, CFG);
    }

    static boolean pareceSaldoAnterior(String descricao) {
        String n = FaturaPdfLayoutSupport.norm(descricao);
        return n.contains("saldo fatura anterior") || n.contains("saldo da fatura anterior");
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
