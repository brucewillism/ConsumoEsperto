package com.consumoesperto.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta comandos de texto para importar fatura PDF protegida (senha CPF — Itaú/Inter).
 */
public final class FaturaPdfSenhaWhatsappTextParser {

    private static final Pattern COMANDO_FATURA_SENHA = Pattern.compile(
        "(?is)(?:exporte|importe|importar|processar|abrir)\\s+(?:essa\\s+)?fatura"
            + "(?:\\s+para\\s+o\\s+banco)?\\s*(.+)?"
    );

    private static final Pattern BANCO_NO_TEXTO = Pattern.compile(
        "(?i)\\b(?:banco\\s+)?(itau|itaú|inter|bradesco|santander|nubank|bb|banco\\s+do\\s+brasil)\\b"
    );

    private static final Pattern SENHA_APOS_CODIGO = Pattern.compile(
        "(?is)(?:codigo|código|senha|password)"
            + "(?:\\s+para\\s+abrir(?:\\s+a\\s+fatura)?)?"
            + "\\s+(?:e\\s+)?(\\d{4,11})"
    );

    private static final Pattern SENHA_SOLTA = Pattern.compile("^\\s*(\\d{4,11})\\s*$");

    private FaturaPdfSenhaWhatsappTextParser() {}

    public record ParsedFaturaSenha(String senha, String banco) {}

    /**
     * Comando completo, ex.: "importe essa fatura para o banco itau o codigo para abrir a fatura e 1234".
     */
    public static ParsedFaturaSenha parseComando(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        Matcher cmd = COMANDO_FATURA_SENHA.matcher(t);
        if (!cmd.find()) {
            return null;
        }
        String tail = cmd.group(1) != null ? cmd.group(1).trim() : "";
        String alvo = tail.isBlank() ? t : tail;
        String senha = extrairSenha(alvo);
        if (senha == null) {
            senha = extrairSenha(t);
        }
        if (senha == null) {
            return null;
        }
        String banco = extrairBanco(alvo);
        if (banco == null) {
            banco = extrairBanco(t);
        }
        return new ParsedFaturaSenha(senha, banco);
    }

    /** Legenda do PDF ou resposta curta com só os dígitos da senha. */
    public static String extrairSenhaSolta(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        ParsedFaturaSenha cmd = parseComando(t);
        if (cmd != null) {
            return cmd.senha();
        }
        Matcher soDigitos = SENHA_SOLTA.matcher(t);
        if (soDigitos.matches()) {
            return soDigitos.group(1);
        }
        return extrairSenha(t);
    }

    public static String extrairSenha(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = SENHA_APOS_CODIGO.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        Matcher solta = SENHA_SOLTA.matcher(text.trim());
        if (solta.matches()) {
            return solta.group(1);
        }
        return null;
    }

    public static String extrairBanco(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = BANCO_NO_TEXTO.matcher(normalizar(text));
        if (!m.find()) {
            return null;
        }
        String b = m.group(1).toLowerCase(Locale.ROOT);
        if (b.contains("ita")) {
            return "Itaú";
        }
        if (b.contains("inter")) {
            return "Inter";
        }
        if (b.contains("bradesco")) {
            return "Bradesco";
        }
        if (b.contains("santander")) {
            return "Santander";
        }
        if (b.contains("nubank")) {
            return "Nubank";
        }
        if (b.contains("brasil") || b.equals("bb")) {
            return "Banco do Brasil";
        }
        return capitalize(b);
    }

    public static boolean pareceComandoImportarFatura(String raw) {
        return parseComando(raw) != null;
    }

    public static boolean pareceErroPdfProtegido(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return false;
        }
        String m = mensagem.toLowerCase(Locale.ROOT);
        return m.contains("protegido por senha") || m.contains("nao consegui abrir o pdf com a senha")
            || m.contains("não consegui abrir o pdf com a senha");
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
