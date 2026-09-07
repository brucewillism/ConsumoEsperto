package com.consumoesperto.model;

/**
 * Tipo de arquivo no fluxo único de importação (PDF de fatura ou CSV de extrato).
 */
public enum FinancialImportFileType {
    INVOICE_PDF,
    BANK_STATEMENT_CSV,
    CARD_STATEMENT_CSV,
    NEEDS_REVIEW,
    UNKNOWN
}
