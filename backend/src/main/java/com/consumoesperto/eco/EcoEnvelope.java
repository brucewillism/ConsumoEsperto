package com.consumoesperto.eco;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class EcoEnvelope {

    String traceId;
    String spanId;
    String parentSpanId;
    String userId;
    String appOrigin;
    String appCurrent;
    String conversationId;
    String taskId;
    String toolCallId;
    EcoMode mode;
    Instant deadline;
    EcoSensitivity sensitivity;
    boolean synthesized;

    public boolean deadlineExceeded() {
        return deadline != null && !deadline.isAfter(Instant.now());
    }

    public Duration remaining() {
        if (deadline == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(Instant.now(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Encurta o deadline; nunca estende. */
    public EcoEnvelope shortenDeadline(Duration reserve) {
        if (deadline == null || reserve == null || reserve.isNegative() || reserve.isZero()) {
            return this;
        }
        Instant shortened = deadline.minus(reserve);
        Instant now = Instant.now();
        if (shortened.isBefore(now)) {
            shortened = now;
        }
        if (shortened.isAfter(deadline)) {
            return this;
        }
        return toBuilder().deadline(shortened).build();
    }

    public EcoEnvelope childSpan() {
        return toBuilder()
            .parentSpanId(spanId)
            .spanId(EcoUlid.span())
            .build();
    }

    public EcoEnvelope withRaisedSensitivity(EcoSensitivity incoming) {
        EcoSensitivity current = sensitivity != null ? sensitivity : EcoSensitivity.INTERNAL;
        return toBuilder().sensitivity(current.raiseTo(incoming)).build();
    }

    public Map<String, String> toHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        put(h, EcoHeaders.TRACE_ID, traceId);
        put(h, EcoHeaders.SPAN_ID, spanId);
        put(h, EcoHeaders.PARENT_SPAN_ID, parentSpanId);
        put(h, EcoHeaders.USER_ID, userId);
        put(h, EcoHeaders.APP_ORIGIN, appOrigin);
        put(h, EcoHeaders.APP_CURRENT, appCurrent);
        put(h, EcoHeaders.CONVERSATION_ID, conversationId);
        put(h, EcoHeaders.TASK_ID, taskId);
        put(h, EcoHeaders.TOOL_CALL_ID, toolCallId);
        if (mode != null) {
            h.put(EcoHeaders.MODE, mode.name());
        }
        if (deadline != null) {
            h.put(EcoHeaders.DEADLINE, deadline.toString());
        }
        if (sensitivity != null) {
            h.put(EcoHeaders.SENSITIVITY, sensitivity.name());
        }
        return h;
    }

    private static void put(Map<String, String> h, String key, String value) {
        if (value != null && !value.isBlank()) {
            h.put(key, value);
        }
    }
}
