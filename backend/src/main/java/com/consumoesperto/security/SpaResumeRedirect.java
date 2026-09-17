package com.consumoesperto.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Quando o proxy manda uma rota SPA para o Spring, devolve 302 para {@code /}
 * com o caminho original em {@code ce_resume}, para o Angular repor a URL.
 */
final class SpaResumeRedirect {

    static final String QUERY_PARAM = "ce_resume";
    private static final int MAX_RESUME_CHARS = 512;

    private SpaResumeRedirect() {}

    static boolean redirectIfBrowserDocument(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        if (!BrowserDocumentRequest.matches(request)) {
            return false;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Location", location(request));
        return true;
    }

    static String location(HttpServletRequest request) {
        String resume = resumePath(request);
        if (resume == null || resume.equals("/") || resume.equalsIgnoreCase("/index.html")) {
            return "/";
        }
        return "/?" + QUERY_PARAM + "=" + urlEncode(resume);
    }

    static String resumePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return "/";
        }
        String query = request.getQueryString();
        String full = path;
        if (query != null && !query.isBlank()) {
            String cleaned = stripNestedResume(query);
            if (!cleaned.isBlank()) {
                full = path + "?" + cleaned;
            }
        }
        if (!isSafeRelativePath(full)) {
            return "/";
        }
        return full;
    }

    static boolean isSafeRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_RESUME_CHARS) {
            return false;
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '\r' || c == '\n' || c == '\0') {
                return false;
            }
        }
        return !value.contains("://");
    }

    private static String stripNestedResume(String query) {
        String[] parts = query.split("&");
        StringBuilder kept = new StringBuilder();
        for (String part : parts) {
            if (part.startsWith(QUERY_PARAM + "=") || part.isEmpty()) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(part);
        }
        return kept.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
