package com.consumoesperto.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Correspondência aproximada para termos de busca (ex.: "Gazolina" → descrição com "Gasolina").
 */
public final class StringFuzzy {

    private StringFuzzy() {
    }

    public static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Verifica se a descrição corresponde ao termo com tolerância a erros de digitação.
     */
    public static boolean descricaoContemTermoFuzzy(String descricao, String termo) {
        if (termo == null || termo.isBlank()) {
            return false;
        }
        String d = normalizar(descricao);
        String p = normalizar(termo);
        if (d.isEmpty() || p.length() < 2) {
            return false;
        }
        if (d.contains(p)) {
            return true;
        }
        int maxDist = Math.max(1, Math.min(3, p.length() / 3 + 1));
        for (String w : d.split("\\s+")) {
            if (w.length() < 2) {
                continue;
            }
            if (levenshtein(w, p) <= maxDist) {
                return true;
            }
        }
        return levenshtein(d, p) <= maxDist + 1;
    }

    public static int levenshtein(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
