package com.consumoesperto.security;

import com.consumoesperto.config.EvolutionWebhookAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Autenticação fail-closed do webhook Evolution — header customizado e/ou token na query.
 * Rollback: {@code evolution.webhook.auth-required=false}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookApiKeyFilter extends OncePerRequestFilter {

    private final EvolutionWebhookAuthProperties authProperties;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return !path.contains("/api/public/evolution/webhook")
            && !path.contains("/api/whatsapp/webhook");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!authProperties.isAuthRequired()) {
            log.warn("[WEBHOOK-AUTH] auth-required=false — rollback ativo; webhook aceito sem credencial path={}",
                request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String expected = resolveExpectedSecret();
        if (expected == null || expected.isBlank()) {
            log.warn("[WEBHOOK-AUTH] REJEITADO: auth-required=true mas segredo vazio (defina evolution.webhook.secret ou evolution.apikey) path={} ip={}",
                request.getRequestURI(), request.getRemoteAddr());
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "webhook-secret-not-configured");
            return;
        }

        String received = extractCredential(request);
        if (received == null || !expected.trim().equals(received.trim())) {
            String motivo = received == null ? "credencial-ausente" : "credencial-invalida";
            log.warn("[WEBHOOK-AUTH] REJEITADO: {} path={} ip={} header={} queryParam={}",
                motivo,
                request.getRequestURI(),
                request.getRemoteAddr(),
                authProperties.getHeaderName(),
                authProperties.getQueryParam());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, motivo);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveExpectedSecret() {
        if (authProperties.getSecret() != null && !authProperties.getSecret().isBlank()) {
            return authProperties.getSecret();
        }
        return evolutionApiKey;
    }

    private String extractCredential(HttpServletRequest request) {
        String headerName = authProperties.getHeaderName();
        if (headerName != null && !headerName.isBlank()) {
            String fromHeader = request.getHeader(headerName);
            if (fromHeader != null && !fromHeader.isBlank()) {
                return stripBearer(fromHeader);
            }
        }
        if (authProperties.isAcceptLegacyApikeyHeader()) {
            String legacy = firstNonBlank(
                request.getHeader("apikey"),
                request.getHeader("Apikey")
            );
            if (legacy != null) {
                return stripBearer(legacy);
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            return stripBearer(authHeader);
        }
        String queryParam = authProperties.getQueryParam();
        if (queryParam != null && !queryParam.isBlank()) {
            String fromQuery = request.getParameter(queryParam);
            if (fromQuery != null && !fromQuery.isBlank()) {
                return fromQuery.trim();
            }
        }
        return null;
    }

    private static String stripBearer(String value) {
        if (value != null && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value != null ? value.trim() : null;
    }

    private static void reject(HttpServletResponse response, int status, String reason) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\",\"reason\":\"" + reason + "\"}");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
