package com.consumoesperto.util;

import com.consumoesperto.model.MemoriaMetadados;
import com.consumoesperto.model.MemoriaTipo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extração heurística (sem LLM) de metadados de memória a partir de texto pt-BR:
 * valor em reais, mês-alvo e tipo (PLANO_FUTURO/PREFERENCIA/FATO).
 */
public final class MemoriaTextoHeuristica {

    private static final Pattern RE_VALOR = Pattern.compile(
        "R\\$\\s*([\\d]{1,3}(?:\\.[\\d]{3})*(?:,\\d{2})?|[\\d]+(?:,\\d{2})?)"
            + "|\\b([\\d]{1,3}(?:\\.[\\d]{3})*(?:,\\d{2})?|[\\d]+(?:,\\d{2})?)\\s*(?:reais|conto[s]?|pila[s]?)\\b"
            + "|\\b(\\d+(?:[.,]\\d+)?)\\s*(?:mil|k)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern RE_FUTURO = Pattern.compile(
        "\\b(vou|irei|pretendo|planejo|quero|preciso|tenho que|vamos)\\b.{0,80}?"
            + "\\b(gastar|comprar|pagar|viajar|reformar|trocar|investir|fazer|quitar|matricular)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern RE_PREFERENCIA = Pattern.compile(
        "\\b(prefiro|n[aã]o gosto|gosto de|sempre uso|nunca uso|evito|libera|liberar|priorizo|odeio)\\b",
        Pattern.CASE_INSENSITIVE);

    private MemoriaTextoHeuristica() {
    }

    /** Enriquece metadados com valor, mês-alvo, ano-alvo e validade extraídos do texto. */
    public static MemoriaMetadados enriquecer(MemoriaMetadados base, String texto, LocalDate hoje) {
        if (base == null || texto == null || texto.isBlank()) {
            return base;
        }
        MemoriaMetadados meta = base;
        if (meta.valor() == null) {
            BigDecimal v = extrairValor(texto);
            if (v != null) {
                meta = meta.comValor(v);
            }
        }
        if (meta.mesAlvo() == null) {
            int[] alvo = extrairMesAnoAlvo(texto, hoje);
            if (alvo != null) {
                meta = meta.comAlvo(alvo[0], alvo[1]);
                if (meta.tipo() == MemoriaTipo.PLANO_FUTURO && meta.validade() == null) {
                    // «cirurgia em julho» expira em agosto: 1º dia do mês seguinte ao alvo
                    meta = meta.comValidade(LocalDate.of(alvo[1], alvo[0], 1).plusMonths(1));
                }
            }
        }
        return meta;
    }

    /** Classifica o tipo mais provável do texto: PLANO_FUTURO, PREFERENCIA ou FATO. */
    public static MemoriaTipo detectarTipo(String texto, LocalDate hoje) {
        if (texto == null || texto.isBlank()) {
            return MemoriaTipo.FATO;
        }
        if (RE_FUTURO.matcher(texto).find() && extrairMesAnoAlvo(texto, hoje) != null) {
            return MemoriaTipo.PLANO_FUTURO;
        }
        if (RE_PREFERENCIA.matcher(texto).find()) {
            return MemoriaTipo.PREFERENCIA;
        }
        return MemoriaTipo.FATO;
    }

    public static BigDecimal extrairValor(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = RE_VALOR.matcher(texto);
        if (!m.find()) {
            return null;
        }
        try {
            if (m.group(3) != null) {
                // «15 mil» / «2k»
                BigDecimal base = new BigDecimal(m.group(3).replace(',', '.'));
                return base.multiply(new BigDecimal("1000")).setScale(2, RoundingMode.HALF_UP);
            }
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            raw = raw.replace(".", "").replace(',', '.');
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Mês/ano alvo citado no texto: nome do mês em pt-BR, «mês que vem», «próximo mês».
     * Mês já passado no ano corrente assume o próximo ano. Devolve {@code null} se nada citado.
     */
    public static int[] extrairMesAnoAlvo(String texto, LocalDate hoje) {
        if (texto == null || hoje == null) {
            return null;
        }
        String n = normalizar(texto);
        if (n.contains("mes que vem") || n.contains("proximo mes")) {
            LocalDate alvo = hoje.plusMonths(1);
            return new int[] {alvo.getMonthValue(), alvo.getYear()};
        }
        for (Map.Entry<String, Integer> e : meses().entrySet()) {
            if (!n.contains(e.getKey())) {
                continue;
            }
            int mes = e.getValue();
            Matcher anoM = Pattern.compile("\\b(20[0-9]{2})\\b").matcher(n);
            int ano = anoM.find() ? Integer.parseInt(anoM.group(1)) : hoje.getYear();
            if (ano == hoje.getYear() && mes < hoje.getMonthValue()) {
                ano++;
            }
            return new int[] {mes, ano};
        }
        return null;
    }

    private static Map<String, Integer> meses() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("janeiro", 1);
        m.put("fevereiro", 2);
        m.put("marco", 3);
        m.put("abril", 4);
        m.put("maio", 5);
        m.put("junho", 6);
        m.put("julho", 7);
        m.put("agosto", 8);
        m.put("setembro", 9);
        m.put("outubro", 10);
        m.put("novembro", 11);
        m.put("dezembro", 12);
        return m;
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
