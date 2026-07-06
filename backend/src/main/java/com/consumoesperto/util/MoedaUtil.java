package com.consumoesperto.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Parsing e distribuição monetária sem ponto flutuante. */
public final class MoedaUtil {

    private static final RoundingMode ARRED = RoundingMode.HALF_UP;
    private static final int SCALE = 2;

    private MoedaUtil() {}

    public static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(SCALE, ARRED) : BigDecimal.ZERO.setScale(SCALE, ARRED);
    }

    /** Lê valor monetário de JSON sem {@code asDouble()} (evita perda de centavos). */
    public static BigDecimal fromJson(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return new BigDecimal(n.asText()).setScale(SCALE, ARRED);
            }
            return parseBr(n.asText(""));
        } catch (Exception e) {
            return null;
        }
    }

    public static BigDecimal fromJsonField(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return null;
        }
        return fromJson(parent.get(field));
    }

    public static BigDecimal parseBr(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.replace("R$", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.matches(".*\\d+[.,]\\d{3}([.,]\\d{2})?.*") || (t.contains(",") && t.lastIndexOf(',') > t.indexOf('.'))) {
            t = t.replace(".", "").replace(",", ".");
        } else {
            t = t.replace(",", ".");
        }
        return new BigDecimal(t.trim()).setScale(SCALE, ARRED);
    }

    /**
     * Divide {@code total} em {@code parcelas} partes iguais; resíduo de centavos na última parcela.
     */
    public static List<BigDecimal> distribuirParcelas(BigDecimal total, int parcelas) {
        if (parcelas <= 0) {
            return List.of();
        }
        BigDecimal t = nz(total);
        if (parcelas == 1) {
            return List.of(t);
        }
        BigDecimal base = t.divide(BigDecimal.valueOf(parcelas), SCALE, RoundingMode.DOWN);
        List<BigDecimal> out = new ArrayList<>(parcelas);
        BigDecimal acumulado = BigDecimal.ZERO.setScale(SCALE, ARRED);
        for (int i = 1; i < parcelas; i++) {
            out.add(base);
            acumulado = acumulado.add(base);
        }
        out.add(t.subtract(acumulado).setScale(SCALE, ARRED));
        return out;
    }

    /**
     * Compara valores monetários com tolerância percentual (default 10%) e piso absoluto opcional.
     */
    public static boolean valoresProximos(
        BigDecimal a,
        BigDecimal b,
        BigDecimal toleranciaPct,
        BigDecimal pisoAbsoluto
    ) {
        if (a == null || b == null) {
            return false;
        }
        BigDecimal ref = a.abs().max(b.abs());
        if (ref.compareTo(BigDecimal.ZERO) == 0) {
            return a.compareTo(b) == 0;
        }
        BigDecimal pct = toleranciaPct != null ? toleranciaPct : BigDecimal.TEN;
        BigDecimal tolPct = ref.multiply(pct).divide(BigDecimal.valueOf(100), SCALE, ARRED);
        BigDecimal piso = pisoAbsoluto != null ? pisoAbsoluto : new BigDecimal("2.00");
        BigDecimal tol = tolPct.max(piso);
        return a.subtract(b).abs().compareTo(tol) <= 0;
    }
}
