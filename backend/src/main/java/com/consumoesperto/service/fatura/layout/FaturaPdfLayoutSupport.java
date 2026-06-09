package com.consumoesperto.service.fatura.layout;

import java.text.Normalizer;
import java.util.Locale;

public final class FaturaPdfLayoutSupport {

    private FaturaPdfLayoutSupport() {
    }

    public static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static boolean contem(String textoNorm, String... tokens) {
        if (textoNorm == null || textoNorm.isBlank() || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isBlank() && textoNorm.contains(norm(token))) {
                return true;
            }
        }
        return false;
    }

    public static boolean pareceFaturaCartao(String textoNorm) {
        return contem(
            textoNorm,
            "fatura",
            "vencimento",
            "pagamento minimo",
            "data de vencimento",
            "fechamento da fatura",
            "total da fatura",
            "lancamentos no cartao",
            "transacoes de",
            "movimentacoes na fatura"
        );
    }
}
