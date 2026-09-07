package com.consumoesperto.service.importacao;

import com.consumoesperto.model.FinancialImportFileType;

public record FinancialImportDetection(
    FinancialImportFileType type,
    String mimeEfetivo,
    String encoding,
    char delimiter,
    String motivo
) {
    public boolean isCsv() {
        return type == FinancialImportFileType.BANK_STATEMENT_CSV
            || type == FinancialImportFileType.CARD_STATEMENT_CSV
            || type == FinancialImportFileType.NEEDS_REVIEW;
    }

    public boolean isPdf() {
        return type == FinancialImportFileType.INVOICE_PDF;
    }
}
