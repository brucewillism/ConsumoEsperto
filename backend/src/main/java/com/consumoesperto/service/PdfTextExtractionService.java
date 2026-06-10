package com.consumoesperto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfTextExtractionService {

    private static final Pattern DATA_BR = Pattern.compile("\\d{2}/\\d{2}(?:/\\d{2,4})?");
    private static final Pattern VALOR_BR = Pattern.compile("\\d{1,3}(?:\\.\\d{3})*,\\d{2}");

    /**
     * Uma entrada por página (índice 0 = primeira página).
     */
    public List<String> extrairTextoPorPagina(byte[] pdfBytes) {
        return extrairTextoPorPagina(pdfBytes, null);
    }

    public List<String> extrairTextoPorPagina(byte[] pdfBytes, String senhaInformada) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF vazio");
        }
        List<String> senhas = montarSenhasTentativa(senhaInformada);
        for (String senha : senhas) {
            List<String> paginas = tentarExtrairPaginas(pdfBytes, senha);
            if (temTextoUtil(paginas)) {
                if (senha != null) {
                    log.info("PDF desbloqueado com senha informada ({} página(s) com texto).", paginas.size());
                }
                return paginas;
            }
        }
        if (pdfPareceCriptografado(pdfBytes)) {
            throw new IllegalArgumentException(mensagemPdfProtegidoItau(senhaInformada));
        }
        return fallbackBinario(pdfBytes);
    }

    public String extrairTexto(byte[] pdfBytes) {
        return extrairTexto(pdfBytes, null);
    }

    public String extrairTexto(byte[] pdfBytes, String senhaInformada) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF vazio");
        }
        StringBuilder sb = new StringBuilder();
        for (String p : extrairTextoPorPagina(pdfBytes, senhaInformada)) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(p);
            }
        }
        return sb.toString().trim();
    }

    public boolean pdfPareceCriptografado(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 32) {
            return false;
        }
        int amostra = Math.min(pdfBytes.length, 120_000);
        String amostraTxt = new String(pdfBytes, 0, amostra, StandardCharsets.ISO_8859_1);
        return amostraTxt.contains("/Encrypt");
    }

    public boolean textoPareceFaturaLegivel(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        if (texto.length() < 120) {
            return false;
        }
        return DATA_BR.matcher(texto).find() && VALOR_BR.matcher(texto).find();
    }

    public static String mensagemPdfProtegidoItau(String senhaInformada) {
        if (senhaInformada == null || senhaInformada.isBlank()) {
            return "Este PDF está protegido por senha (faturas Itaú usam os *5 primeiros dígitos do CPF*). "
                + "Informe essa senha no campo abaixo e envie o PDF novamente.";
        }
        return "Não consegui abrir o PDF com a senha informada. "
            + "Nas faturas Itaú, use os *5 primeiros dígitos do CPF* (somente números, sem pontos).";
    }

    private List<String> tentarExtrairPaginas(byte[] pdfBytes, String senha) {
        List<String> paginas = new ArrayList<>();
        try {
            Class<?> readerClass = Class.forName("com.lowagie.text.pdf.PdfReader");
            Object reader = abrirReader(readerClass, pdfBytes, senha);
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
        } catch (Exception e) {
            log.debug("Falha ao extrair PDF senha={}: {}", senha != null ? "***" : "(vazia)", e.getMessage());
        }
        return paginas;
    }

    private static Object abrirReader(Class<?> readerClass, byte[] pdfBytes, String senha) throws Exception {
        if (senha != null && !senha.isBlank()) {
            return readerClass.getConstructor(byte[].class, byte[].class)
                .newInstance(pdfBytes, senha.getBytes(StandardCharsets.UTF_8));
        }
        return readerClass.getConstructor(byte[].class).newInstance((Object) pdfBytes);
    }

    private static List<String> montarSenhasTentativa(String senhaInformada) {
        Set<String> senhas = new LinkedHashSet<>();
        senhas.add(null);
        if (senhaInformada == null || senhaInformada.isBlank()) {
            return new ArrayList<>(senhas);
        }
        String trimmed = senhaInformada.trim();
        String digitos = trimmed.replaceAll("\\D", "");
        if (!trimmed.isBlank()) {
            senhas.add(trimmed);
        }
        if (digitos.length() >= 5) {
            senhas.add(digitos.substring(0, 5));
        }
        if (digitos.length() >= 4) {
            senhas.add(digitos.substring(0, 4));
        }
        return new ArrayList<>(senhas);
    }

    private boolean temTextoUtil(List<String> paginas) {
        if (paginas == null || paginas.isEmpty()) {
            return false;
        }
        String joined = String.join("\n", paginas);
        return textoPareceFaturaLegivel(joined);
    }

    private List<String> fallbackBinario(byte[] pdfBytes) {
        String fallback = new String(pdfBytes, StandardCharsets.ISO_8859_1)
            .replaceAll("[^\\p{L}\\p{N}\\p{Punct}\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (!textoPareceFaturaLegivel(fallback)) {
            log.warn("Texto do PDF insuficiente após extração ({} chars).", fallback.length());
            return List.of("");
        }
        List<String> paginas = new ArrayList<>();
        paginas.add(fallback);
        return paginas;
    }
}
