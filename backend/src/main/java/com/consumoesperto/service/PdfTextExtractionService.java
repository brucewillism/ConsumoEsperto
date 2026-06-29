package com.consumoesperto.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
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

        boolean criptografado = pdfPareceCriptografado(pdfBytes);
        String senhaLimpa = senhaInformada != null ? senhaInformada.trim() : "";

        if (!criptografado) {
            if (!senhaLimpa.isBlank()) {
                log.debug("Senha informada ignorada: PDF não está criptografado.");
            }
            return extrairPdfAberto(pdfBytes);
        }

        List<String> paginas = tentarExtrairPaginas(pdfBytes, null);
        if (temTextoUtil(paginas)) {
            return paginas;
        }
        if (senhaLimpa.isBlank()) {
            throw new IllegalArgumentException(mensagemPdfProtegidoItau(null));
        }
        for (String senha : montarVariantesSenha(senhaInformada)) {
            paginas = tentarExtrairPaginas(pdfBytes, senha);
            if (temTextoUtil(paginas)) {
                log.info("PDF desbloqueado ({} página(s) com texto).", paginas.size());
                return paginas;
            }
        }
        throw new IllegalArgumentException(mensagemPdfProtegidoItau(senhaInformada));
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
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            return doc.isEncrypted();
        } catch (InvalidPasswordException e) {
            return true;
        } catch (IOException e) {
            return contemEncryptNoTrailer(pdfBytes);
        }
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
            return "Este PDF está protegido por senha. Informe a senha apenas nesses casos: "
                + "Itaú (5 primeiros dígitos do CPF) ou Inter (6 primeiros dígitos). "
                + "Nubank, Mercado Pago e a maioria dos bancos enviam PDF aberto — deixe o campo de senha vazio.";
        }
        return "Não consegui abrir o PDF com a senha informada. "
            + "Itaú: 5 primeiros dígitos do CPF; Inter: 6 primeiros dígitos (somente números). "
            + "Se o banco for Nubank ou outro sem senha, apague o campo e envie de novo.";
    }

    /** Variantes da senha informada (somente PDFs criptografados). */
    static List<String> montarVariantesSenha(String senhaInformada) {
        Set<String> senhas = new LinkedHashSet<>();
        if (senhaInformada == null) {
            return List.of();
        }
        String trimmed = senhaInformada.trim();
        String digitos = trimmed.replaceAll("\\D", "");
        if (!trimmed.isBlank()) {
            senhas.add(trimmed);
        }
        if (!digitos.isBlank()) {
            senhas.add(digitos);
            if (digitos.length() >= 4) {
                senhas.add(digitos.substring(0, 4));
            }
            if (digitos.length() >= 5) {
                senhas.add(digitos.substring(0, 5));
            }
            if (digitos.length() >= 6) {
                senhas.add(digitos.substring(0, 6));
            }
            if (digitos.length() <= 11) {
                String cpf11 = ("00000000000" + digitos).substring(("00000000000" + digitos).length() - 11);
                senhas.add(cpf11);
                senhas.add(cpf11.substring(0, 5));
                senhas.add(cpf11.substring(0, 6));
            }
        }
        return new ArrayList<>(senhas);
    }

    private List<String> extrairPdfAberto(byte[] pdfBytes) {
        List<String> paginas = tentarExtrairPaginas(pdfBytes, null);
        if (temTextoUtil(paginas)) {
            return paginas;
        }
        if (paginas.stream().anyMatch(p -> p != null && !p.isBlank())) {
            return paginas;
        }
        List<String> fallback = fallbackBinario(pdfBytes);
        if (temTextoUtil(fallback) || fallback.stream().anyMatch(p -> p != null && !p.isBlank())) {
            return fallback;
        }
        return paginas.isEmpty() ? fallback : paginas;
    }

    private static boolean contemEncryptNoTrailer(byte[] pdfBytes) {
        int[] offsets = {0, Math.max(0, pdfBytes.length - 65_536)};
        for (int offset : offsets) {
            int len = offset == 0
                ? Math.min(pdfBytes.length, 65_536)
                : pdfBytes.length - offset;
            String chunk = new String(pdfBytes, offset, len, StandardCharsets.ISO_8859_1);
            if (chunk.contains("/Encrypt")) {
                return true;
            }
        }
        return false;
    }

    private List<String> tentarExtrairPaginas(byte[] pdfBytes, String senha) {
        List<String> pdfBox = tentarExtrairComPdfBox(pdfBytes, senha);
        if (!pdfBox.isEmpty()) {
            return pdfBox;
        }
        return tentarExtrairComOpenPdf(pdfBytes, senha);
    }

    private List<String> tentarExtrairComPdfBox(byte[] pdfBytes, String senha) {
        List<String> paginas = new ArrayList<>();
        try (PDDocument doc = abrirPdfBox(pdfBytes, senha)) {
            if (doc == null) {
                return paginas;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            int total = doc.getNumberOfPages();
            for (int i = 1; i <= total; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(doc);
                paginas.add(pageText != null ? pageText.trim() : "");
            }
        } catch (IOException e) {
            log.debug("PDFBox falhou senha={}: {}", senha != null ? "***" : "(vazia)", e.getMessage());
        } catch (Exception e) {
            log.debug("PDFBox erro inesperado: {}", e.getMessage());
        }
        return paginas;
    }

    private static PDDocument abrirPdfBox(byte[] pdfBytes, String senha) throws IOException {
        if (senha != null && !senha.isBlank()) {
            return PDDocument.load(pdfBytes, senha);
        }
        return PDDocument.load(pdfBytes);
    }

    private List<String> tentarExtrairComOpenPdf(byte[] pdfBytes, String senha) {
        List<String> paginas = new ArrayList<>();
        Object reader = null;
        try {
            Class<?> readerClass = Class.forName("com.lowagie.text.pdf.PdfReader");
            reader = abrirReaderOpenPdf(readerClass, pdfBytes, senha);
            int pages = (Integer) readerClass.getMethod("getNumberOfPages").invoke(reader);
            if (pages <= 0) {
                return paginas;
            }
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
        } catch (Exception e) {
            log.debug("OpenPDF falhou senha={}: {}", senha != null ? "***" : "(vazia)", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.getClass().getMethod("close").invoke(reader);
                } catch (Exception ignored) {
                    // close best effort
                }
            }
        }
        return paginas;
    }

    private static Object abrirReaderOpenPdf(Class<?> readerClass, byte[] pdfBytes, String senha) throws Exception {
        if (senha == null || senha.isBlank()) {
            return readerClass.getConstructor(byte[].class).newInstance((Object) pdfBytes);
        }
        Exception last = null;
        for (byte[] pwdBytes : variantesSenhaBytes(senha)) {
            try {
                return readerClass.getConstructor(byte[].class, byte[].class)
                    .newInstance(pdfBytes, pwdBytes);
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        return readerClass.getConstructor(byte[].class).newInstance((Object) pdfBytes);
    }

    private static List<byte[]> variantesSenhaBytes(String senha) {
        List<byte[]> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String variante : montarVariantesSenha(senha)) {
            if (!seen.add(variante)) {
                continue;
            }
            out.add(variante.getBytes(StandardCharsets.UTF_8));
            out.add(variante.getBytes(StandardCharsets.ISO_8859_1));
            out.add(variante.getBytes(Charset.forName("Windows-1252")));
        }
        return out;
    }

    private boolean temTextoUtil(List<String> paginas) {
        if (paginas == null || paginas.isEmpty()) {
            return false;
        }
        String joined = String.join("\n", paginas);
        if (textoPareceFaturaLegivel(joined)) {
            return true;
        }
        if (joined.length() < 400) {
            return false;
        }
        String n = joined.toLowerCase(Locale.ROOT);
        boolean pareceFatura = n.contains("fatura")
            || n.contains("vencimento")
            || n.contains("lancamento")
            || n.contains("lançamento")
            || n.contains("banco inter")
            || n.contains("itau");
        return pareceFatura && (DATA_BR.matcher(joined).find() || VALOR_BR.matcher(joined).find());
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
