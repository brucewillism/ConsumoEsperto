package com.consumoesperto.service.importacao.csv;

import com.consumoesperto.model.FinancialImportFileType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialCsvParserRegistry {

    private final List<FinancialCsvParser> parsers;

    public CsvParseResult parse(byte[] bytes, String fileName, FinancialImportFileType detectedType) {
        CsvEncodingDetector.Result enc = CsvEncodingDetector.detect(bytes);
        String sample = enc.text().length() > 2000 ? enc.text().substring(0, 2000) : enc.text();
        return parsers.stream()
            .sorted(Comparator.comparingInt(FinancialCsvParser::order))
            .filter(p -> p.supports(fileName, sample, detectedType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Nenhum parser CSV disponível."))
            .parse(bytes, fileName, detectedType);
    }
}
