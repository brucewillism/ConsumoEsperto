package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.dto.ImportedFinancialRow;
import com.consumoesperto.model.FinancialImportFileType;

import java.util.List;

public record CsvParseResult(
    List<ImportedFinancialRow> rows,
    int totalLines,
    int valid,
    int invalid,
    char delimiter,
    String encoding,
    String layout,
    FinancialImportFileType suggestedType
) {}
