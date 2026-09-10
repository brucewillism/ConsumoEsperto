package com.consumoesperto.security;

import com.consumoesperto.exception.ApiError;
import com.consumoesperto.util.LogSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta HTTPS atrás de proxy (Nginx/Apache/Cloudflare).
 * {@code request.isSecure()} fica {@code false} quando o TLS termina no proxy e o Spring
 * recebe HTTP — a menos que {@code server.forward-headers-strategy} + {@code X-Forwarded-Proto}
 * estejam correctos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForwardedHttps {

    public static final String ERROR_CODE = "HTTPS_REQUIRED";
    public static final String MESSAGE =
        "HTTPS obrigatório. A URL https:// no telemóvel não chega ao Spring se o proxy "
            + "termina o TLS e encaminha HTTP sem X-Forwarded-Proto.";
    public static final String INSTRUCAO =
        "No Nginx: proxy_set_header X-Forwarded-Proto $scheme; "
            + "No Apache: RequestHeader set X-Forwarded-Proto \"https\". "
            + "Em desenvolvimento local: MOBILE_CAPTURE_REQUIRE_HTTPS=false "
            + "ou INGEST_NOTIFICACAO_REQUIRE_HTTPS=false.";

    private static final Pattern RFC7239_PROTO = Pattern.compile(
        "(?i)(?:^|;|\\s)proto=\"?([a-z0-9]+)\"?");
    private static final Pattern CF_VISITOR_SCHEME = Pattern.compile(
        "(?i)\"scheme\"\\s*:\\s*\"(https)\"");

    private final ObjectMapper objectMapper;

    public static boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        if ("https".equalsIgnoreCase(trimToNull(request.getScheme()))) {
            return true;
        }
        String proto = firstForwardedProto(request.getHeader("X-Forwarded-Proto"));
        if (proto == null) {
            proto = firstForwardedProto(request.getHeader("X-Forwarded-Protocol"));
        }
        if ("https".equalsIgnoreCase(proto)) {
            return true;
        }
        if (rfc7239FirstHopIsHttps(request.getHeader("Forwarded"))) {
            return true;
        }
        if (flagOn(request.getHeader("X-Forwarded-Ssl"))
            || flagOn(request.getHeader("Front-End-Https"))) {
            return true;
        }
        return cfVisitorIsHttps(request.getHeader("CF-Visitor"));
    }

    public void rejectRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        log.warn(
            "https_obrigatorio path={} isSecure={} scheme={} headers=[{}]",
            LogSanitizer.sanitize(request.getRequestURI()),
            request.isSecure(),
            LogSanitizer.sanitize(request.getScheme()),
            LogSanitizer.sanitize(forwardedHeadersSnapshot(request))
        );
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scheme", request.getScheme());
        details.put("secure", request.isSecure());
        details.put("forwardedProto", forwardedProto);
        ApiError body = new ApiError(
            ERROR_CODE,
            MESSAGE,
            INSTRUCAO,
            HttpServletResponse.SC_FORBIDDEN,
            request.getRequestURI() != null ? request.getRequestURI() : "",
            details
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    static String firstForwardedProto(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String first = header.split(",")[0].trim();
        if (first.regionMatches(true, 0, "proto=", 0, 6)) {
            first = first.substring(6).trim();
        }
        first = unquote(first);
        return first.isEmpty() ? null : first;
    }

    static boolean rfc7239FirstHopIsHttps(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) {
            return false;
        }
        String first = forwarded.split(",")[0];
        Matcher m = RFC7239_PROTO.matcher(first);
        return m.find() && "https".equalsIgnoreCase(m.group(1));
    }

    static boolean cfVisitorIsHttps(String cfVisitor) {
        if (cfVisitor == null || cfVisitor.isBlank()) {
            return false;
        }
        return CF_VISITOR_SCHEME.matcher(cfVisitor).find();
    }

    private static boolean flagOn(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        return "on".equalsIgnoreCase(v)
            || "true".equalsIgnoreCase(v)
            || "1".equals(v)
            || "https".equalsIgnoreCase(v);
    }

    private static String forwardedHeadersSnapshot(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            appendKnown(sb, request);
            return sb.toString();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x-forwarded")
                || "forwarded".equals(lower)
                || "cf-visitor".equals(lower)
                || "front-end-https".equals(lower)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(name).append('=').append(nullToEmpty(request.getHeader(name)));
            }
        }
        if (sb.length() == 0) {
            appendKnown(sb, request);
        }
        return sb.toString();
    }

    private static void appendKnown(StringBuilder sb, HttpServletRequest request) {
        appendOne(sb, "X-Forwarded-Proto", request.getHeader("X-Forwarded-Proto"));
        appendOne(sb, "X-Forwarded-For", request.getHeader("X-Forwarded-For"));
        appendOne(sb, "X-Forwarded-Host", request.getHeader("X-Forwarded-Host"));
        appendOne(sb, "Forwarded", request.getHeader("Forwarded"));
    }

    private static void appendOne(StringBuilder sb, String name, String value) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(name).append('=').append(nullToEmpty(value));
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
