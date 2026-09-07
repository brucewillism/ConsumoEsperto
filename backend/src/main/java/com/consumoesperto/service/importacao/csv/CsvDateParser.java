package com.consumoesperto.service.importacao.csv;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class CsvDateParser {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("d/M/yy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ISO_LOCAL_DATE
    );

    private CsvDateParser() {}

    public static LocalDate parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        int t = s.indexOf('T');
        int space = s.indexOf(' ');
        if (t > 8) {
            s = s.substring(0, t);
        } else if (space > 8 && s.length() > 12) {
            s = s.substring(0, space);
        }
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                // tenta o próximo
            }
        }
        return null;
    }
}
