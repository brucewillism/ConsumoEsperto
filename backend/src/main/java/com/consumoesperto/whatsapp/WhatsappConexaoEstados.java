package com.consumoesperto.whatsapp;

import java.util.Locale;

/** Normaliza estados da Evolution ({@code connectionState} / {@code CONNECTION_UPDATE}). */
public final class WhatsappConexaoEstados {

    public static final String OPEN = "open";
    public static final String CONNECTING = "connecting";
    public static final String CLOSE = "close";
    public static final String MISSING = "missing";
    public static final String ERROR = "error";
    public static final String SUPPRESSED = "suppressed";

    private WhatsappConexaoEstados() {}

    public static String normalizar(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLOSE;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("open".equals(s) || "connected".equals(s) || "online".equals(s)) {
            return OPEN;
        }
        if ("connecting".equals(s) || "pairing".equals(s)) {
            return CONNECTING;
        }
        if ("close".equals(s) || "closed".equals(s) || "disconnected".equals(s)
            || "disconnect".equals(s) || "logout".equals(s) || "refused".equals(s)
            || "offline".equals(s)) {
            return CLOSE;
        }
        if (MISSING.equals(s) || ERROR.equals(s) || SUPPRESSED.equals(s)) {
            return s;
        }
        return CLOSE;
    }

    public static boolean isSessaoOk(String estado) {
        return OPEN.equals(normalizar(estado));
    }

    public static boolean isQueda(String estado) {
        String n = normalizar(estado);
        return CLOSE.equals(n) || MISSING.equals(n) || ERROR.equals(n);
    }
}
