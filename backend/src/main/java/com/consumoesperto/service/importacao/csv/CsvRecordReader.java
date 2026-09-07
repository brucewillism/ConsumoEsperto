package com.consumoesperto.service.importacao.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * Splitter CSV com aspas, delimitador variável e quebra de linha dentro de campo.
 */
public final class CsvRecordReader {

    private CsvRecordReader() {}

    public static List<List<String>> read(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                i++;
                continue;
            }
            if (c == delimiter) {
                current.add(field.toString());
                field.setLength(0);
                i++;
                continue;
            }
            if (c == '\r') {
                i++;
                continue;
            }
            if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                if (!isTotallyEmpty(current)) {
                    rows.add(current);
                }
                current = new ArrayList<>();
                i++;
                continue;
            }
            field.append(c);
            i++;
        }
        current.add(field.toString());
        if (!isTotallyEmpty(current)) {
            rows.add(current);
        }
        return rows;
    }

    public static char detectDelimiter(String headerLine) {
        int semi = countUnquoted(headerLine, ';');
        int comma = countUnquoted(headerLine, ',');
        int tab = countUnquoted(headerLine, '\t');
        if (tab >= semi && tab >= comma && tab > 0) {
            return '\t';
        }
        if (semi >= comma && semi > 0) {
            return ';';
        }
        if (comma > 0) {
            return ',';
        }
        return ';';
    }

    public static int countUnquoted(String line, char delimiter) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && c == delimiter) {
                count++;
            }
        }
        return count;
    }

    private static boolean isTotallyEmpty(List<String> row) {
        if (row.isEmpty()) {
            return true;
        }
        for (String c : row) {
            if (c != null && !c.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
