package com.consumoesperto.edith.tools;

import com.consumoesperto.eco.EcoException;

/**
 * Teto rígido de {@code limit}. Pedido acima do máximo é {@code INVALID_INPUT},
 * nunca truncado em silêncio.
 */
public final class ToolLimits {

    public static final int SEARCH_DEFAULT = 50;
    public static final int SEARCH_MAX = 100;
    public static final int LIST_DEFAULT = 20;
    public static final int LIST_MAX = 50;
    public static final int CATEGORY_DEFAULT = 12;
    public static final int CATEGORY_MAX = 30;

    private ToolLimits() {
    }

    public static int require(Object raw, int defaultVal, int max) {
        if (raw == null) {
            return defaultVal;
        }
        int value;
        try {
            value = raw instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw EcoException.invalidInput("limit inválido");
        }
        if (value < 1) {
            throw EcoException.invalidInput("limit deve ser >= 1");
        }
        if (value > max) {
            throw EcoException.invalidInput("limit acima do teto " + max);
        }
        return value;
    }
}
