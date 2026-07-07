package com.consumoesperto.service.jarvis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser determinístico pt-BR para comandos de alta frequência — evita chamada ao LLM quando a confiança é alta.
 */
@Component
public class JarvisFastPathParser {

    private static final Pattern DESPESA = Pattern.compile(
        "(?i)(?:gastei|paguei|comprei|despesa|gasto)\\s+(?:de\\s+)?"
            + "(R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+(?:[,.]\\d{1,2})?)"
            + "\\s*(?:reais?|real)?"
            + "(?:\\s+(?:no|na|em|do|da|de)\\s+)?(.+)$");

    private static final Pattern RECEITA = Pattern.compile(
        "(?i)(?:recebi|receita|entrada|ganhei)\\s+(?:de\\s+)?"
            + "(R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+(?:[,.]\\d{1,2})?)"
            + "\\s*(?:reais?|real)?\\s*(?:de\\s+)?(.+)?$");

    private static final Pattern AMBIGUO = Pattern.compile(
        "(?i)(?:paguei|gastei|comprei)\\s+(?:aquele|aquela|isso|negocio|negócio|la|lá)\\b");

    public Optional<JsonNode> tryParse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String t = text.trim();
        if (AMBIGUO.matcher(t).find()) {
            return Optional.empty();
        }
        Matcher mDesp = DESPESA.matcher(t);
        if (mDesp.find()) {
            BigDecimal valor = parseValor(mDesp.group(2));
            if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            String desc = limparDescricao(mDesp.group(3));
            if (desc.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(comando("CREATE_EXPENSE", valor, desc, 0.92));
        }
        Matcher mRec = RECEITA.matcher(t);
        if (mRec.find()) {
            BigDecimal valor = parseValor(mRec.group(2));
            if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            String desc = mRec.group(3) != null ? limparDescricao(mRec.group(3)) : "Receita";
            if (desc.isBlank()) {
                desc = "Receita";
            }
            return Optional.of(comando("CREATE_INCOME", valor, desc, 0.92));
        }
        return Optional.empty();
    }

    public boolean isAmbiguous(String text) {
        return text != null && AMBIGUO.matcher(text.trim()).find();
    }

    private static ObjectNode comando(String action, BigDecimal amount, String description, double confianca) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("action", action);
        n.put("amount", amount);
        n.put("description", description);
        n.put("confianca", confianca);
        n.put("parseSource", "FAST_PATH");
        return n;
    }

    static BigDecimal parseValor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String x = raw.replace("R$", "").trim();
        if (x.contains(",") && x.contains(".")) {
            x = x.replace(".", "").replace(",", ".");
        } else if (x.contains(",")) {
            x = x.replace(",", ".");
        }
        try {
            return new BigDecimal(x).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String limparDescricao(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.trim();
        d = d.replaceAll("(?i)\\s+no\\s+cart[aã]o.*$", "");
        d = d.replaceAll("(?i)\\s+em\\s+\\d+\\s*(?:x|vezes|parcelas?).*$", "");
        return d.trim();
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
