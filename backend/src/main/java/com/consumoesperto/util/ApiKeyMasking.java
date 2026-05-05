package com.consumoesperto.util;

/**
 * Ofuscação de chaves para respostas HTTP (nunca expor valor completo após salvo).
 */
public final class ApiKeyMasking {

    private ApiKeyMasking() {
    }

    public static String maskApiKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 4) {
            return "••••";
        }
        String last4 = key.substring(key.length() - 4);
        if (key.startsWith("sk-")) {
            int third = key.indexOf('-', 3);
            if (third > 0) {
                return key.substring(0, third + 1) + "••••••••" + last4;
            }
        }
        if (key.startsWith("gsk_")) {
            return "gsk_••••••••" + last4;
        }
        return "••••••••••" + last4;
    }
}
