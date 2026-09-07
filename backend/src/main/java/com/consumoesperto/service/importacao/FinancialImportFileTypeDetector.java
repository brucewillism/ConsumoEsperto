package com.consumoesperto.service.importacao;

import com.consumoesperto.model.FinancialImportFileType;
import com.consumoesperto.service.importacao.csv.CsvEncodingDetector;
import com.consumoesperto.service.importacao.csv.CsvHeaderNormalizer;
import com.consumoesperto.service.importacao.csv.CsvRecordReader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class FinancialImportFileTypeDetector {

    public static final int MAX_CSV_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PDF_BYTES = 10 * 1024 * 1024;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    public FinancialImportDetection detect(String fileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Envie um ficheiro no campo «file».");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT).trim();
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (startsWith(bytes, PDF_MAGIC) || name.endsWith(".pdf") || mime.contains("pdf")) {
            if (!startsWith(bytes, PDF_MAGIC)) {
                throw new IllegalArgumentException("O ficheiro não é um PDF válido.");
            }
            if (bytes.length > MAX_PDF_BYTES) {
                throw new IllegalArgumentException("O PDF excede o tamanho máximo permitido.");
            }
            return new FinancialImportDetection(
                FinancialImportFileType.INVOICE_PDF, "application/pdf", null, '\0', "assinatura PDF");
        }
        if (bytes.length > MAX_CSV_BYTES) {
            throw new IllegalArgumentException("O CSV excede o tamanho máximo de 8 MB.");
        }
        boolean mimeCsv = mime.contains("csv")
            || mime.equals("text/plain")
            || mime.equals("application/vnd.ms-excel")
            || mime.isBlank();
        boolean extCsv = name.endsWith(".csv") || name.endsWith(".txt") || name.isEmpty();
        if (!mimeCsv && !extCsv) {
            return new FinancialImportDetection(
                FinancialImportFileType.UNKNOWN, mime, null, '\0', "extensão/MIME não suportados");
        }
        CsvEncodingDetector.Result enc;
        try {
            enc = CsvEncodingDetector.detect(bytes);
        } catch (IllegalArgumentException ex) {
            throw ex;
        }
        if (!pareceCsv(enc.text())) {
            if (name.endsWith(".csv") || mime.contains("csv")) {
                throw new IllegalArgumentException("O conteúdo não corresponde a um CSV válido.");
            }
            return new FinancialImportDetection(
                FinancialImportFileType.UNKNOWN, mime, enc.name(), '\0', "conteúdo não é CSV");
        }
        String first = firstNonEmptyLine(enc.text());
        char delimiter = CsvRecordReader.detectDelimiter(first);
        FinancialImportFileType csvType = classifyCsv(name, enc.text(), delimiter);
        String motivo = csvType == FinancialImportFileType.NEEDS_REVIEW
            ? "CSV válido, mas conta vs cartão exige confirmação"
            : "CSV válido";
        return new FinancialImportDetection(csvType, mime.isBlank() ? "text/csv" : mime, enc.name(), delimiter, motivo);
    }

    static boolean pareceCsv(String text) {
        String first = firstNonEmptyLine(text);
        if (first == null) {
            return false;
        }
        char d = CsvRecordReader.detectDelimiter(first);
        int seps = CsvRecordReader.countUnquoted(first, d);
        return seps >= 1 && first.length() < 4000;
    }

    FinancialImportFileType classifyCsv(String fileName, String text, char delimiter) {
        String sample = text.length() > 8000 ? text.substring(0, 8000) : text;
        String n = norm(sample + " " + fileName);
        int card = 0;
        int bank = 0;
        if (n.contains("estabelecimento") || n.contains("final do cartao") || n.contains("final_do_cartao")
            || n.contains("fatura") || n.contains("parcela") || n.contains("cartao de credito")) {
            card += 2;
        }
        if (fileName != null && (fileName.contains("fatura") || fileName.contains("cartao") || fileName.contains("credit"))) {
            card += 2;
        }
        if (n.contains("historico") || n.contains("agencia") || n.contains("saldo")
            || n.contains(" ted") || n.contains("pix enviado") || n.contains("conta corrente")) {
            bank += 2;
        }
        if (fileName != null && (fileName.contains("extrato") || fileName.contains("conta") || fileName.contains("bank"))) {
            bank += 2;
        }
        try {
            List<List<String>> rows = CsvRecordReader.read(sample, delimiter);
            if (!rows.isEmpty()) {
                List<String> header = rows.get(0);
                String headerNorm = CsvHeaderNormalizer.normalize(String.join(" ", header));
                if (headerNorm.contains("estabelecimento") || headerNorm.contains("cartao")) {
                    card += 2;
                }
                if (headerNorm.contains("historico") || headerNorm.contains("debito") || headerNorm.contains("saldo")) {
                    bank += 2;
                }
            }
        } catch (RuntimeException ignored) {
            // classificação usa só sinais textuais
        }
        if (card >= bank + 2 && card >= 2) {
            return FinancialImportFileType.CARD_STATEMENT_CSV;
        }
        if (bank >= card + 2 && bank >= 2) {
            return FinancialImportFileType.BANK_STATEMENT_CSV;
        }
        return FinancialImportFileType.NEEDS_REVIEW;
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String firstNonEmptyLine(String text) {
        for (String line : text.split("\\R")) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    private static String norm(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
