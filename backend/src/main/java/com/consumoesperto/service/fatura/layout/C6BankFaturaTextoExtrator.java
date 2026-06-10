package com.consumoesperto.service.fatura.layout;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class C6BankFaturaTextoExtrator {

    private static final FaturaTextoExtratorPadrao.BancoTextoConfig CFG = new FaturaTextoExtratorPadrao.BancoTextoConfig(
        "C6 Bank",
        new String[] { "lancamentos", "lançamentos", "detalhamento da fatura", "transacoes do cartao" },
        FaturaTextoExtratorPadrao.fimProximasFaturas(),
        new java.util.regex.Pattern[] { FaturaTextoExtratorPadrao.padraoTotalPadrao() },
        new String[] { "c6 bank", "c6 carbon" },
        FaturaTextoExtratorPadrao::pareceEncargoComum,
        d -> false
    );

    private C6BankFaturaTextoExtrator() {
    }

    static boolean deveIgnorarDescricao(String descricao) {
        return FaturaTextoExtratorPadrao.deveIgnorar(descricao, CFG);
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
}
