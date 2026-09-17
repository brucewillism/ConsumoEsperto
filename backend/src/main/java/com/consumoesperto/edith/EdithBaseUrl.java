package com.consumoesperto.edith;

import java.net.URI;
import java.util.Set;

/**
 * A API E.D.I.T.H. não é o Control Center / Vite. Portas típicas de frontend
 * ({@code 5173}, {@code 4173}, {@code 4200}, {@code 3000}) não são base de API.
 */
public final class EdithBaseUrl {

    static final Set<Integer> FRONTEND_PORTS = Set.of(5173, 4173, 4200, 3000, 5174);

    private EdithBaseUrl() {}

    public static boolean looksLikeFrontend(String baseUrl) {
        Integer port = portOf(baseUrl);
        return port != null && FRONTEND_PORTS.contains(port);
    }

    public static Integer portOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            int port = uri.getPort();
            if (port > 0) {
                return port;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return 443;
            }
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                return 80;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Sugestão apenas diagnóstica — não reescreve a config. */
    public static String suggestedApiUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank() || !looksLikeFrontend(baseUrl)) {
            return baseUrl;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            int port = uri.getPort();
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            if (port == 5173 || port == 4173 || port == 5174) {
                return scheme + "://" + host + ":8000";
            }
            return scheme + "://" + host + ":8000";
        } catch (Exception e) {
            return "http://127.0.0.1:8000";
        }
    }
}
