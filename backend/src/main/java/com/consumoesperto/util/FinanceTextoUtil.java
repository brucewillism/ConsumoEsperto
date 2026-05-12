package com.consumoesperto.util;

import java.text.Normalizer;
import java.util.Locale;

/** Normalização de descrições para padrões de hábito / sequência. */
public final class FinanceTextoUtil {

    private FinanceTextoUtil() {
    }

    public static String chaveAgrupamento(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return "_vazio_";
        }
        String n = Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (n.length() > 48) {
            n = n.substring(0, 48).trim();
        }
        return n.isBlank() ? "_vazio_" : n;
    }

    public static String rotuloAmigavel(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return "este estabelecimento";
        }
        String t = descricao.trim();
        if (t.length() > 42) {
            return t.substring(0, 39) + "...";
        }
        return t;
    }
}
