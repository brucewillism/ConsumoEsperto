package com.consumoesperto.util;

/**
 * Montagem segura da URL da Evolution API: evita barras duplas ({@code //}) entre base e path,
 * que em algumas versões (ex.: v2.x) resultam em 404.
 */
public final class EvolutionUrlSupport {

    private EvolutionUrlSupport() {
    }

    /**
     * Remove espaços nas pontas e todas as barras finais repetidas da URL base (ex.: {@code http://host:8585///}
     * → {@code http://host:8585}).
     */
    public static String sanitizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Concatena base Evolution + segmento de path com exactamente uma {@code '/'} entre eles.
     * O segmento pode vir com ou sem barra inicial (ex.: {@code /message/sendText/x} ou {@code message/sendText/x}).
     *
     * @param evolutionBaseUrl valor típico de {@code evolution.url} / {@code EVOLUTION_URL}
     * @param pathSegment restante do path (pode incluir vários níveis separados por '/')
     */
    public static String joinEvolutionPath(String evolutionBaseUrl, String pathSegment) {
        String base = sanitizeBaseUrl(evolutionBaseUrl);
        if (base.isEmpty()) {
            return "";
        }
        if (pathSegment == null || pathSegment.isBlank()) {
            return base;
        }
        String path = pathSegment.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return base + "/" + path;
    }
}
