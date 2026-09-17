package com.consumoesperto.security;

import javax.servlet.http.HttpServletRequest;

/**
 * Distingue navegação do browser (F5 / URL directa em rota Angular) de chamada à API.
 */
final class BrowserDocumentRequest {

    private BrowserDocumentRequest() {}

    static boolean matches(HttpServletRequest request) {
        if (request == null || request.getMethod() == null
            || !"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if (isBackendPath(request.getRequestURI())) {
            return false;
        }
        if (isDocumentNavigation(request)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        String a = accept.toLowerCase();
        return a.contains("text/html") || a.contains("application/xhtml+xml");
    }

    private static boolean isBackendPath(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        String p = path.toLowerCase();
        return p.equals("/api")
            || p.startsWith("/api/")
            || p.startsWith("/actuator")
            || p.startsWith("/swagger")
            || p.startsWith("/v3/api-docs")
            || p.startsWith("/capabilities");
    }

    private static boolean isDocumentNavigation(HttpServletRequest request) {
        String dest = request.getHeader("Sec-Fetch-Dest");
        String mode = request.getHeader("Sec-Fetch-Mode");
        return "document".equalsIgnoreCase(dest) && "navigate".equalsIgnoreCase(mode);
    }
}
