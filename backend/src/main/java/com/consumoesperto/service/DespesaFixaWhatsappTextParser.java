package com.consumoesperto.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta frases livres de cadastro de despesa fixa (WhatsApp / J.A.R.V.I.S.).
 */
public final class DespesaFixaWhatsappTextParser {

    private DespesaFixaWhatsappTextParser() {}

    public record ParsedDespesaFixa(BigDecimal valor, String descricao, Integer diaVencimento) {}

    public static ParsedDespesaFixa parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        ParsedDespesaFixa lembrete = parseLembreteVencimento(t);
        if (lembrete != null) {
            return lembrete;
        }
        Matcher m1 = Pattern.compile(
            "(?i)(?:salve\\s+)?(?:essa\\s+)?despesa\\s+fixa\\s+de\\s*R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?\\s*(?:para|pro|pra)\\s+(.+)"
        ).matcher(t);
        if (m1.find()) {
            return montar(m1.group(1), m1.group(2));
        }
        Matcher m4 = Pattern.compile(
            "(?i)despesa\\s+fixa\\s*(?:de\\s*)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?\\s*(?:para|pro|pra)\\s+(.+)"
        ).matcher(t);
        if (m4.find()) {
            return montar(m4.group(1), m4.group(2));
        }
        Matcher m5 = Pattern.compile(
            "(?i)(?:cadastr(?:e|ar|a)|registr(?:e|ar|a)|cri(?:e|ar|a))(?:\\s+uma)?(?:\\s+nova)?\\s+despesa\\s+fixa\\s+(.+)"
        ).matcher(t);
        if (m5.find()) {
            return parseFraseLivre(m5.group(1).trim());
        }
        Matcher m6 = Pattern.compile("(?i)nova\\s+despesa\\s+fixa\\s+(.+)").matcher(t);
        if (m6.find()) {
            return parseFraseLivre(m6.group(1).trim());
        }
        Matcher m2 = Pattern.compile("(?i)adicione\\s+(?:uma\\s+)?despesa\\s+fixa\\s*:?\\s*(.+)").matcher(t);
        if (m2.find()) {
            return parseFraseLivre(m2.group(1).trim());
        }
        Matcher m3 = Pattern.compile("(?i)(?:cadastr(?:e|ar|a)|registr(?:e|ar|a))\\s+despesa\\s+fixa\\s*:?\\s*(.+)").matcher(t);
        if (m3.find()) {
            return parseFraseLivre(m3.group(1).trim());
        }
        return null;
    }

    /** Ex.: "conta de luz vence dia 10", "lembrete internet de 250 vence dia 5". */
    static ParsedDespesaFixa parseLembreteVencimento(String t) {
        Matcher valorAntes = Pattern.compile(
            "(?i)(?:cadastr(?:e|ar|a)|cri(?:e|ar|a)|registr(?:e|ar|a)|salve)?\\s*(?:um\\s+)?(?:lembrete\\s+(?:de\\s+)?)?(.+?)\\s+(?:de\\s+|no\\s+valor\\s+(?:de\\s+)?)R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?\\s+vence\\s+dia\\s*(\\d{1,2})\\s*$"
        ).matcher(t);
        if (valorAntes.find()) {
            return montarOpcional(valorAntes.group(2), valorAntes.group(1), parseDia(valorAntes.group(3)));
        }
        Matcher venceDia = Pattern.compile(
            "(?i)(?:cadastr(?:e|ar|a)|cri(?:e|ar|a)|registr(?:e|ar|a)|salve)?\\s*(?:um\\s+)?(?:lembrete\\s+(?:de\\s+)?)?(.+?)\\s+vence\\s+dia\\s*(\\d{1,2})(?:\\s+(?:de\\s+|no\\s+valor\\s+(?:de\\s+)?|valor\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?)?\\s*$"
        ).matcher(t);
        if (venceDia.find()) {
            return montarOpcional(venceDia.group(3), venceDia.group(1), parseDia(venceDia.group(2)));
        }
        return null;
    }

    private static Integer parseDia(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int d = Integer.parseInt(text.trim());
        return d >= 1 && d <= 31 ? d : null;
    }

    private static ParsedDespesaFixa montarOpcional(String valorStr, String descRaw, Integer dia) {
        String desc = sanitize(descRaw);
        if (desc.isBlank() || dia == null) {
            return null;
        }
        BigDecimal v = valorStr != null && !valorStr.isBlank() ? parseMoney(valorStr) : null;
        if (v != null && v.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (v != null) {
            v = v.setScale(2, RoundingMode.HALF_UP);
        }
        return new ParsedDespesaFixa(v, desc, dia);
    }

    static ParsedDespesaFixa parseFraseLivre(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return null;
        }
        String c = chunk.trim();
        ParsedDespesaFixa lista = parseListaVirgula(c);
        if (lista != null) {
            return lista;
        }
        Matcher valorNome = Pattern.compile(
            "(?i)com\\s+(?:o\\s+)?valor\\s+(?:de\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?\\s+com\\s+(?:o\\s+)?nome\\s+(?:de\\s+)?(.+)"
        ).matcher(c);
        if (valorNome.find()) {
            return montar(valorNome.group(1), valorNome.group(2));
        }
        Matcher nomeValor = Pattern.compile(
            "(?i)com\\s+(?:o\\s+)?nome\\s+(?:de\\s+)?(.+?)\\s+com\\s+(?:o\\s+)?valor\\s+(?:de\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?"
        ).matcher(c);
        if (nomeValor.find()) {
            return montar(nomeValor.group(2), nomeValor.group(1));
        }
        Matcher valorPara = Pattern.compile(
            "(?i)(?:com\\s+(?:o\\s+)?)?valor\\s+(?:de\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?\\s+(?:para|pro|pra|com\\s+(?:o\\s+)?nome\\s+(?:de\\s+)?)(.+)"
        ).matcher(c);
        if (valorPara.find()) {
            return montar(valorPara.group(1), valorPara.group(2));
        }
        Matcher nomeSimples = Pattern.compile(
            "(?i)(?:com\\s+(?:o\\s+)?nome\\s+(?:de\\s+)?)?(.+?)\\s+(?:no\\s+)?valor\\s+(?:de\\s+)?R?\\$?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:reais?)?"
        ).matcher(c);
        if (nomeSimples.find()) {
            return montar(nomeSimples.group(2), nomeSimples.group(1));
        }
        return null;
    }

    private static ParsedDespesaFixa parseListaVirgula(String chunk) {
        String[] parts = chunk.split("\\s*,\\s*");
        if (parts.length < 2) {
            return null;
        }
        String descPart = sanitize(parts[0].trim());
        if (descPart.isBlank()) {
            return null;
        }
        BigDecimal v = parseMoney(parts[1]);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        Integer dia = null;
        if (parts.length >= 3) {
            dia = extrairPrimeiroDia(parts[2]);
        }
        if (dia == null) {
            dia = extrairDiaDoUltimoTrecho(chunk);
        }
        return new ParsedDespesaFixa(v.setScale(2, RoundingMode.HALF_UP), descPart, dia);
    }

    private static ParsedDespesaFixa montar(String valorStr, String descTail) {
        BigDecimal v = parseMoney(valorStr);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String work = descTail == null ? "" : descTail.trim();
        Integer dia = extrairDiaDoUltimoTrecho(work);
        if (dia != null) {
            work = removerUltimoDiaTrecho(work);
        }
        work = sanitize(work);
        if (work.isBlank()) {
            return null;
        }
        return new ParsedDespesaFixa(v.setScale(2, RoundingMode.HALF_UP), work, dia);
    }

    private static Integer extrairDiaDoUltimoTrecho(String work) {
        if (work == null || work.isBlank()) {
            return null;
        }
        String s = work.trim();
        Matcher m = Pattern.compile("(?i)(?:,\\s*)?(?:todo\\s+)?dia\\s*(\\d{1,2})\\s*$").matcher(s);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            return d >= 1 && d <= 31 ? d : null;
        }
        m = Pattern.compile("(?i)(?:,\\s*)?venc(?:imento)?(?:\\s+do\\s+m[eê]s)?\\s*(?:dia\\s*)?(\\d{1,2})\\s*$").matcher(s);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            return d >= 1 && d <= 31 ? d : null;
        }
        return null;
    }

    private static String removerUltimoDiaTrecho(String work) {
        if (work == null) {
            return "";
        }
        String s = work.trim();
        s = Pattern.compile("(?i)(?:,\\s*)?(?:todo\\s+)?dia\\s*\\d{1,2}\\s*$").matcher(s).replaceFirst("").trim();
        s = Pattern.compile("(?i)(?:,\\s*)?venc(?:imento)?(?:\\s+do\\s+m[eê]s)?\\s*(?:dia\\s*)?\\d{1,2}\\s*$").matcher(s).replaceFirst("").trim();
        return s.replaceAll(",\\s*$", "").trim();
    }

    private static Integer extrairPrimeiroDia(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,2})").matcher(text.trim());
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            if (d >= 1 && d <= 31) {
                return d;
            }
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
