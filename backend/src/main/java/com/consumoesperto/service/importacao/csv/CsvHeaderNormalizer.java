package com.consumoesperto.service.importacao.csv;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CsvHeaderNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private CsvHeaderNormalizer() {}

    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String n = Normalizer.normalize(header, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
        n = n.replace('\u00A0', ' ');
        return NON_ALNUM.matcher(n).replaceAll(" ").trim().replace(' ', '_');
    }
}
