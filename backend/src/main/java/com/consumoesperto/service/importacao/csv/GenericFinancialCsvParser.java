package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.dto.ImportedFinancialRow;
import com.consumoesperto.model.FinancialImportFileType;
import com.consumoesperto.model.ImportedRowKind;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(100)
public class GenericFinancialCsvParser implements FinancialCsvParser {

    public static final int MAX_ROWS = 20_000;

    @Override
    public boolean supports(String fileName, String sampleText, FinancialImportFileType detectedType) {
        return true;
    }

    @Override
    public CsvParseResult parse(byte[] bytes, String fileName, FinancialImportFileType detectedType) {
        CsvEncodingDetector.Result enc = CsvEncodingDetector.detect(bytes);
        String text = enc.text();
        String firstLine = firstNonEmptyLine(text);
        if (firstLine == null) {
            throw new IllegalArgumentException("O CSV não contém linhas utilizáveis.");
        }
        char delimiter = CsvRecordReader.detectDelimiter(firstLine);
        List<List<String>> table = CsvRecordReader.read(text, delimiter);
        if (table.isEmpty()) {
            throw new IllegalArgumentException("O CSV não contém linhas utilizáveis.");
        }
        if (table.size() - 1 > MAX_ROWS) {
            throw new IllegalArgumentException(
                "O CSV excede o limite de " + MAX_ROWS + " linhas de lançamento.");
        }
        List<String> header = table.get(0);
        Map<CsvColumnMapper.Role, Integer> cols = CsvColumnMapper.mapHeader(header);
        boolean headerOk = CsvColumnMapper.temColunasMinimas(cols);
        if (!headerOk) {
            throw new IllegalArgumentException(
                "Cabeçalho do CSV não reconhecido. É preciso pelo menos data, descrição/histórico e valor.");
        }
        boolean cardContext = detectedType == FinancialImportFileType.CARD_STATEMENT_CSV;
        List<ImportedFinancialRow> rows = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        for (int i = 1; i < table.size(); i++) {
            List<String> line = table.get(i);
            int sourceLine = i + 1;
            ImportedFinancialRow row = parseRow(line, cols, sourceLine, cardContext);
            rows.add(row);
            if (row.isValid()) {
                valid++;
            } else {
                invalid++;
            }
        }
        FinancialImportFileType suggested = detectedType;
        return new CsvParseResult(rows, table.size() - 1, valid, invalid, delimiter, enc.name(), layoutName(), suggested);
    }

    private ImportedFinancialRow parseRow(
        List<String> line,
        Map<CsvColumnMapper.Role, Integer> cols,
        int sourceLine,
        boolean cardContext
    ) {
        ImportedFinancialRow row = new ImportedFinancialRow();
        row.setSourceLine(sourceLine);
        row.setCurrency("BRL");
        String dateRaw = cell(line, cols, CsvColumnMapper.Role.DATE);
        String descRaw = firstNonBlank(
            cell(line, cols, CsvColumnMapper.Role.DESCRIPTION),
            cell(line, cols, CsvColumnMapper.Role.MERCHANT)
        );
        String merchantRaw = firstNonBlank(
            cell(line, cols, CsvColumnMapper.Role.MERCHANT),
            descRaw
        );
        String amountRaw = cell(line, cols, CsvColumnMapper.Role.AMOUNT);
        String debitRaw = cell(line, cols, CsvColumnMapper.Role.DEBIT);
        String creditRaw = cell(line, cols, CsvColumnMapper.Role.CREDIT);
        String typeRaw = cell(line, cols, CsvColumnMapper.Role.TYPE);
        String instRaw = cell(line, cols, CsvColumnMapper.Role.INSTALLMENT);
        String currency = cell(line, cols, CsvColumnMapper.Role.CURRENCY);
        if (currency != null && !currency.isBlank()) {
            row.setCurrency(currency.trim().toUpperCase(Locale.ROOT));
        }

        LocalDate date = CsvDateParser.parse(dateRaw);
        BigDecimal signed = resolveSignedAmount(amountRaw, debitRaw, creditRaw, typeRaw);
        if (date == null) {
            row.setInvalidReason("Data inválida");
        } else if (signed == null || signed.signum() == 0) {
            row.setInvalidReason("Valor inválido");
        } else if (descRaw == null || descRaw.isBlank()) {
            row.setInvalidReason("Descrição vazia");
        }

        row.setTransactionDate(date);
        row.setDescription(trimTo(descRaw, 200));
        row.setMerchant(trimTo(merchantRaw, 200));
        CsvInstallmentParser.Result inst = CsvInstallmentParser.parse(
            firstNonBlank(instRaw, descRaw));
        if (inst.present()) {
            row.setInstallmentNumber(inst.number());
            row.setInstallmentTotal(inst.total());
        }
        row.setAccountHint(trimTo(cell(line, cols, CsvColumnMapper.Role.CARD), 80));
        row.setCardHint(firstNonBlank(
            cell(line, cols, CsvColumnMapper.Role.CARD_LAST4),
            cell(line, cols, CsvColumnMapper.Role.CARD)
        ));
        row.setExternalId(trimTo(cell(line, cols, CsvColumnMapper.Role.EXTERNAL_ID), 128));
        row.setRawReference(trimTo(String.join("|", line), 300));

        ImportedRowKind kind = FinancialCsvSemantics.classify(descRaw, typeRaw, signed, cardContext);
        row.setTransactionType(kind);
        row.setAmount(toStoredAmount(kind, signed, cardContext));
        return row;
    }

    /**
     * Persistimos magnitude positiva; o tipo da linha define despesa/receita/estorno.
     * Estorno guarda o valor absoluto (a confirmação aplica sinal negativo na fatura).
     */
    static BigDecimal toStoredAmount(ImportedRowKind kind, BigDecimal signed, boolean cardContext) {
        if (signed == null) {
            return null;
        }
        BigDecimal abs = signed.abs();
        if (kind == ImportedRowKind.ESTORNO) {
            return abs;
        }
        if (kind == ImportedRowKind.PAGAMENTO_FATURA) {
            return abs;
        }
        if (!cardContext && signed.signum() < 0 && kind == ImportedRowKind.DESPESA) {
            return abs;
        }
        if (!cardContext && signed.signum() > 0 && kind == ImportedRowKind.RECEITA) {
            return abs;
        }
        if (cardContext) {
            return abs;
        }
        return abs;
    }

    static BigDecimal resolveSignedAmount(String amountRaw, String debitRaw, String creditRaw, String typeRaw) {
        BigDecimal debit = CsvAmountParser.parse(debitRaw);
        BigDecimal credit = CsvAmountParser.parse(creditRaw);
        if (debit != null && debit.signum() != 0 && credit != null && credit.signum() != 0) {
            return credit.abs().subtract(debit.abs());
        }
        if (debit != null && debit.signum() != 0) {
            return debit.signum() > 0 ? debit.negate() : debit;
        }
        if (credit != null && credit.signum() != 0) {
            return credit.abs();
        }
        BigDecimal amount = CsvAmountParser.parse(amountRaw);
        if (amount == null) {
            return null;
        }
        String t = typeRaw == null ? "" : typeRaw.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("d") && !t.contains("cred") || "debito".equals(t) || "saida".equals(t)) {
            return amount.abs().negate();
        }
        if (t.startsWith("c") || "credito".equals(t) || "entrada".equals(t)) {
            return amount.abs();
        }
        return amount;
    }

    private static String cell(List<String> line, Map<CsvColumnMapper.Role, Integer> cols, CsvColumnMapper.Role role) {
        Integer idx = cols.get(role);
        if (idx == null || idx < 0 || idx >= line.size()) {
            return null;
        }
        String v = line.get(idx);
        return v == null ? null : v.trim();
    }

    private static String firstNonEmptyLine(String text) {
        for (String line : text.split("\\R")) {
            if (line != null && !line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }
}
