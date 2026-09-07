package com.consumoesperto.service.importacao.csv;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CsvInstallmentParser {

    private static final Pattern[] PATTERNS = {
        Pattern.compile("(?i)\\bparc(?:ela)?\\s*0?(\\d{1,2})\\s*/\\s*0?(\\d{1,2})\\b"),
        Pattern.compile("\\((\\d{1,2})\\s*/\\s*(\\d{1,2})\\)")
    };

    private CsvInstallmentParser() {}

    public static Result parse(String text) {
        if (text == null || text.isBlank()) {
            return Result.none();
        }
        String s = text.trim();
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                int atual = Integer.parseInt(m.group(1));
                int total = Integer.parseInt(m.group(2));
                if (atual >= 1 && total >= 2 && atual <= total && total <= 48) {
                    return new Result(atual, total);
                }
            }
        }
        return Result.none();
    }

    public record Result(Integer number, Integer total) {
        static Result none() {
            return new Result(null, null);
        }

        boolean present() {
            return number != null && total != null;
        }
    }
}
