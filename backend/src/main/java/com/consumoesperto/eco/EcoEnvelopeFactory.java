package com.consumoesperto.eco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class EcoEnvelopeFactory {

    private EcoEnvelopeFactory() {
    }

    public static EcoEnvelope fromRequest(
        HttpServletRequest request,
        JsonNode bodyEnvelope,
        EcoMode defaultMode,
        EcoSensitivity defaultSensitivity,
        String defaultOrigin
    ) {
        String incomingTrace = first(
            header(request, EcoHeaders.TRACE_ID),
            text(bodyEnvelope, "trace_id")
        );
        String incomingSpan = first(
            header(request, EcoHeaders.SPAN_ID),
            text(bodyEnvelope, "span_id")
        );
        String incomingParent = first(
            header(request, EcoHeaders.PARENT_SPAN_ID),
            text(bodyEnvelope, "parent_span_id")
        );
        String incomingUser = first(
            header(request, EcoHeaders.USER_ID),
            text(bodyEnvelope, "user_id")
        );
        EcoMode mode = EcoMode.parse(
            first(header(request, EcoHeaders.MODE), text(bodyEnvelope, "mode")),
            defaultMode
        );
        Instant deadline = parseDeadline(
            first(
                header(request, EcoHeaders.DEADLINE_TOTAL),
                text(bodyEnvelope, "deadline_total"),
                header(request, EcoHeaders.DEADLINE),
                text(bodyEnvelope, "deadline")
            ),
            mode
        );
        EcoSensitivity sensitivity = EcoSensitivity.parse(
            first(header(request, EcoHeaders.SENSITIVITY), text(bodyEnvelope, "sensitivity")),
            defaultSensitivity
        );

        boolean inboundHop = incomingSpan != null && !incomingSpan.isBlank();
        boolean synthesized = incomingTrace == null || incomingTrace.isBlank();
        String traceId = !synthesized ? incomingTrace : EcoUlid.trace();
        String spanId = EcoUlid.span();
        String parentSpanId = inboundHop ? incomingSpan : emptyToNull(incomingParent);

        return EcoEnvelope.builder()
            .traceId(traceId)
            .spanId(spanId)
            .parentSpanId(parentSpanId)
            .userId(incomingUser)
            .appOrigin(first(header(request, EcoHeaders.APP_ORIGIN), text(bodyEnvelope, "app_origin"), defaultOrigin))
            .appCurrent(first(header(request, EcoHeaders.APP_CURRENT), text(bodyEnvelope, "app_current"), EcoHeaders.APP_CONSUMO))
            .conversationId(first(header(request, EcoHeaders.CONVERSATION_ID), text(bodyEnvelope, "conversation_id")))
            .taskId(first(header(request, EcoHeaders.TASK_ID), text(bodyEnvelope, "task_id")))
            .toolCallId(first(header(request, EcoHeaders.TOOL_CALL_ID), text(bodyEnvelope, "tool_call_id")))
            .mode(mode)
            .deadline(deadline)
            .sensitivity(sensitivity)
            .synthesized(synthesized)
            .build();
    }

    public static JsonNode extractBodyEnvelope(ObjectMapper mapper, byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            return root != null ? root.get("envelope") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static EcoEnvelope fromMap(Map<String, Object> raw, EcoEnvelope base) {
        if (raw == null || raw.isEmpty() || base == null) {
            return base;
        }
        EcoEnvelope.EcoEnvelopeBuilder b = base.toBuilder();
        if (raw.get("trace_id") != null) {
            b.traceId(String.valueOf(raw.get("trace_id")));
        }
        if (raw.get("tool_call_id") != null) {
            b.toolCallId(String.valueOf(raw.get("tool_call_id")));
        }
        if (raw.get("conversation_id") != null) {
            b.conversationId(String.valueOf(raw.get("conversation_id")));
        }
        if (raw.get("task_id") != null) {
            b.taskId(String.valueOf(raw.get("task_id")));
        }
        if (raw.get("user_id") != null) {
            b.userId(String.valueOf(raw.get("user_id")));
        }
        return b.build();
    }

    private static Instant parseDeadline(String raw, EcoMode mode) {
        if (raw != null && !raw.isBlank()) {
            try {
                return Instant.parse(raw.trim());
            } catch (Exception ignored) {
                // cai no default do modo
            }
        }
        Duration budget = mode != null ? mode.defaultBudget() : EcoMode.BALANCED.defaultBudget();
        return Instant.now().plus(budget);
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }

    private static String first(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
