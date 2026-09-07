package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.model.FinancialImportFileType;

public interface FinancialCsvParser {

    boolean supports(String fileName, String sampleText, FinancialImportFileType detectedType);

    CsvParseResult parse(byte[] bytes, String fileName, FinancialImportFileType detectedType);

    default int order() {
        return 100;
    }

    default String layoutName() {
        return getClass().getSimpleName();
    }
}
