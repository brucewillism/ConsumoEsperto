package com.consumoesperto.service.importacao.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Interpreta valores monetários de extratos BR e US, incluindo sinal e parênteses.
 */
public final class CsvAmountParser {

    private static final Pattern LIXO = Pattern.compile("[^0-9,\\.\\-\\(\\)]");

    private CsvAmountParser() {}

    public static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || "-".equals(s) || "—".equals(s)) {
            return null;
        }
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1).trim();
        }
        s = s.replace("R$", "").replace("r$", "").replace("USD", "").replace("EUR", "");
        s = s.replace('\u00A0', ' ').trim();
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1).trim();
        } else if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }
        s = LIXO.matcher(s).replaceAll("");
        if (s.isEmpty()) {
            return null;
        }
        BigDecimal value = parseUnsigned(s);
        if (value == null) {
            return null;
        }
        if (negative) {
            value = value.negate();
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal parseUnsigned(String s) {
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        try {
            if (lastComma >= 0 && lastDot >= 0) {
                if (lastComma > lastDot) {
                    // 1.234,56
                    return new BigDecimal(s.replace(".", "").replace(',', '.'));
                }
                // 1,234.56
                return new BigDecimal(s.replace(",", ""));
            }
            if (lastComma >= 0) {
                int decimals = s.length() - lastComma - 1;
                if (decimals == 3 && s.indexOf(',') == lastComma && s.substring(0, lastComma).matches("\\d{1,3}")) {
                    // 1,234 ambiguo: trata como milhar US se exactamente 3 dígitos após
                    return new BigDecimal(s.replace(",", ""));
                }
                return new BigDecimal(s.replace(".", "").replace(',', '.'));
            }
            if (lastDot >= 0) {
                int decimals = s.length() - lastDot - 1;
                if (decimals == 3 && s.chars().filter(ch -> ch == '.').count() >= 1
                    && decimals != 2) {
                    String[] parts = s.split("\\.");
                    if (parts.length == 2 && parts[1].length() == 3 && parts[0].length() <= 3) {
                        return new BigDecimal(s.replace(".", ""));
                    }
                }
                if (decimals == 3 && s.indexOf('.') == lastDot && !s.contains(",")) {
                    // 1.234 milhar BR sem centavos
                    if (s.substring(0, lastDot).length() <= 3) {
                        return new BigDecimal(s.replace(".", ""));
                    }
                }
                return new BigDecimal(s);
            }
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static BigDecimal abs(BigDecimal v) {
        return v == null ? null : v.abs().setScale(2, RoundingMode.HALF_UP);
    }
}
