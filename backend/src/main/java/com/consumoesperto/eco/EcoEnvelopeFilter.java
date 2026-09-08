package com.consumoesperto.eco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Envelope do ecossistema nos hops cognitivos / tool bridge.
 * Deadline expirado no Tool Bridge responde {@code DEADLINE_EXCEEDED} sem HMAC e sem banco.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
@Slf4j
public class EcoEnvelopeFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !isEdge(path) && !isToolBridge(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        HttpServletRequest effective = request;
        JsonNode bodyEnvelope = null;
        boolean toolBridge = isToolBridge(path(request));
        if (toolBridge && isPost(request)) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            bodyEnvelope = EcoEnvelopeFactory.extractBodyEnvelope(objectMapper, cached.getCachedBody());
            effective = cached;
        }

        EcoMode defaultMode = toolBridge ? EcoMode.BALANCED : EcoMode.BALANCED;
        EcoSensitivity defaultSensitivity = toolBridge ? EcoSensitivity.FINANCIAL : EcoSensitivity.INTERNAL;
        String origin = toolBridge ? EcoHeaders.APP_EDITH : EcoHeaders.APP_JARVIS;
        EcoEnvelope envelope = EcoEnvelopeFactory.fromRequest(
            effective, bodyEnvelope, defaultMode, defaultSensitivity, origin);

        EcoEnvelopeHolder.bind(envelope);
        if (envelope.isSynthesized()) {
            EcoEnvelopeHolder.envelopeSynthesized(true);
        }
        if (path(request).startsWith("/api/ia-chat")) {
            EcoEnvelopeHolder.authScheme("legacy");
        }
        if (toolBridge) {
            EcoEnvelopeHolder.executionPath(EcoExecutionPath.INSTANT);
            envelope = envelope.withRaisedSensitivity(EcoSensitivity.FINANCIAL);
            EcoEnvelopeHolder.replace(envelope);
        }

        try {
            if (shouldEnforceDeadline(effective, toolBridge) && envelope.deadlineExceeded()) {
                EcoEnvelopeHolder.outcome("FAILED");
                writeDeadlineExceeded(response, envelope);
                return;
            }
            writeResponseHeaders(response, envelope);
            filterChain.doFilter(effective, response);
            EcoEnvelopeHolder.current().ifPresent(current -> writeResponseHeaders(response, current));
            if (response.getStatus() >= 400 && "SUCCESS".equals(EcoEnvelopeHolder.outcome())) {
                EcoEnvelopeHolder.outcome("FAILED");
            }
        } catch (EcoDeadlineExceededException e) {
            EcoEnvelopeHolder.outcome("FAILED");
            writeDeadlineExceeded(response, EcoEnvelopeHolder.current().orElse(envelope));
        } finally {
            EcoMetrics.logHop();
            EcoEnvelopeHolder.clear();
        }
    }

    private void writeDeadlineExceeded(HttpServletResponse response, EcoEnvelope envelope) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = Map.of(
            "error", Map.of(
                "code", "DEADLINE_EXCEEDED",
                "message", "deadline excedido antes de executar trabalho",
                "retryable", false,
                "trace_id", envelope.getTraceId() != null ? envelope.getTraceId() : ""
            )
        );
        writeResponseHeaders(response, envelope);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static void writeResponseHeaders(HttpServletResponse response, EcoEnvelope envelope) {
        envelope.toHeaders().forEach(response::setHeader);
    }

    private static boolean isEdge(String path) {
        return path.startsWith("/api/ia-chat")
            || path.startsWith("/api/edith")
            || path.startsWith("/api/capabilities")
            || path.equals("/capabilities");
    }

    private static boolean isToolBridge(String path) {
        return path.startsWith("/api/internal/edith");
    }

    private static boolean shouldEnforceDeadline(HttpServletRequest request, boolean toolBridge) {
        if (toolBridge) {
            return true;
        }
        return isPost(request);
    }

    private static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
