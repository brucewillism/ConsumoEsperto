package com.consumoesperto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class PdfTextExtractionService {

    /**
     * Uma entrada por página (índice 0 = primeira página).
     */
    public java.util.List<String> extrairTextoPorPagina(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF vazio");
        }
        java.util.ArrayList<String> paginas = new java.util.ArrayList<>();
        try {
            Class<?> readerClass = Class.forName("com.lowagie.text.pdf.PdfReader");
            Object reader = readerClass.getConstructor(byte[].class).newInstance((Object) pdfBytes);
            int pages = (Integer) readerClass.getMethod("getNumberOfPages").invoke(reader);
            Class<?> extractorClass = Class.forName("com.lowagie.text.pdf.parser.PdfTextExtractor");
            try {
                Method staticMethod = extractorClass.getMethod("getTextFromPage", readerClass, int.class);
                for (int i = 1; i <= pages; i++) {
                    String pageText = (String) staticMethod.invoke(null, reader, i);
                    paginas.add(pageText != null ? pageText : "");
                }
            } catch (NoSuchMethodException ignored) {
                Constructor<?> ctor = extractorClass.getConstructor(readerClass);
                Object extractor = ctor.newInstance(reader);
                Method instanceMethod = extractorClass.getMethod("getTextFromPage", int.class);
                for (int i = 1; i <= pages; i++) {
                    String pageText = (String) instanceMethod.invoke(extractor, i);
                    paginas.add(pageText != null ? pageText : "");
                }
            }
            try {
                readerClass.getMethod("close").invoke(reader);
            } catch (Exception ignored) {
                // close best effort
            }
            if (paginas.stream().anyMatch(s -> s != null && !s.isBlank())) {
                return paginas;
            }
        } catch (Exception e) {
            log.warn("Falha ao extrair texto do PDF via OpenPDF: {}", e.getMessage());
        }
        String fallback = new String(pdfBytes, StandardCharsets.ISO_8859_1)
            .replaceAll("[^\\p{L}\\p{N}\\p{Punct}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        paginas.clear();
        paginas.add(fallback);
        return paginas;
    }

    public String extrairTexto(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF vazio");
        }
        StringBuilder sb = new StringBuilder();
        for (String p : extrairTextoPorPagina(pdfBytes)) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(p);
            }
        }
        String text = sb.toString().trim();
        if (!text.isBlank()) {
            return text;
        }
        return new String(pdfBytes, StandardCharsets.ISO_8859_1)
            .replaceAll("[^\\p{L}\\p{N}\\p{Punct}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
