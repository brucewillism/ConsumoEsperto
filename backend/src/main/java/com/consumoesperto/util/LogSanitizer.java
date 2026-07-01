package com.consumoesperto.util;

import java.util.regex.Pattern;

/** Evita vazamento de segredos em logs e mensagens de erro. */
public final class LogSanitizer {

    private static final Pattern API_KEY_LIKE = Pattern.compile(
        "(?i)(api[_-]?key|authorization|bearer|token|secret|password|senha)\\s*[:=]\\s*\\S+");
    private static final Pattern JWT_LIKE = Pattern.compile(
        "eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");

    private LogSanitizer() {}

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = API_KEY_LIKE.matcher(raw).replaceAll("$1=***");
        s = JWT_LIKE.matcher(s).replaceAll("eyJ***");
        return s;
    }
}
