package com.consumoesperto.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta frases livres de cadastro de assinatura recorrente (WhatsApp / J.A.R.V.I.S.).
 */
public final class AssinaturaWhatsappTextParser {

    private AssinaturaWhatsappTextParser() {}

    public record ParsedAssinatura(String nome, BigDecimal valor, Integer diaVencimento) {}

    public static ParsedAssinatura parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        Matcher m1 = Pattern.compile(
            "(?i)(?:assinei|assinar|cadastr(?:e|ar|a)|registr(?:e|ar|a)|cri(?:e|ar|a))(?:\\s+uma)?\\s+assinatura(?:\\s+(?:da|de|do))?\\s+(.+)"
        ).matcher(t);
        if (m1.find()) {
            return parseFraseLivre(m1.group(1).trim());
        }
        Matcher m2 = Pattern.compile(
            "(?i)(?:assinei|assinar)\\s+(?:o\\s+)?(?:plano\\s+(?:do|da|de)\\s+)?(.+)"
        ).matcher(t);
        if (m2.find()) {
            return parseFraseLivre(m2.group(1).trim());
        }
        Matcher m3 = Pattern.compile("(?i)nova\\s+assinatura\\s+(.+)").matcher(t);
        if (m3.find()) {
            return parseFraseLivre(m3.group(1).trim());
        }
        return null;
    }

    private static ParsedAssinatura parseFraseLivre(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return null;
        }
        String c = chunk.trim();
        Matcher porMes = Pattern.compile(
            "(?i)(.+?)\\s+por\\s+R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?\\s*(?:por\\s+m[eê]s|mensal|ao\\s+m[eê]s)?"
        ).matcher(c);
        if (porMes.find()) {
            return montar(porMes.group(1), porMes.group(2), c);
        }
        Matcher deValor = Pattern.compile(
            "(?i)(.+?)\\s+(?:de|no\\s+valor\\s+(?:de\\s+)?)R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?"
        ).matcher(c);
        if (deValor.find()) {
            return montar(deValor.group(1), deValor.group(2), c);
        }
        return null;
    }

    private static ParsedAssinatura montar(String nomeRaw, String valorStr, String contextoDia) {
        BigDecimal v = parseMoney(valorStr);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String nome = sanitize(nomeRaw);
        if (nome.isBlank()) {
            return null;
        }
        Integer dia = extrairDia(contextoDia);
        return new ParsedAssinatura(nome, v.setScale(2, RoundingMode.HALF_UP), dia);
    }

    private static Integer extrairDia(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)(?:todo\\s+)?dia\\s*(\\d{1,2})").matcher(text);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            return d >= 1 && d <= 31 ? d : null;
        }
        return null;
    }

    private static BigDecimal parseMoney(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.replace("R$", "").replace("r$", "").trim();
        if (t.matches(".*\\d+[.,]\\d{3}([.,]\\d{2})?.*") || (t.contains(",") && t.lastIndexOf(',') > t.indexOf('.'))) {
            t = t.replace(".", "").replace(",", ".");
        } else {
            t = t.replace(",", ".");
        }
        try {
            return new BigDecimal(t.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^a-zA-Z0-9\\s\\-.,!?()]", "").replaceAll("\\s+", " ").trim();
    }
}
