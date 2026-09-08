package com.consumoesperto.eco;

import org.slf4j.MDC;

import java.util.Optional;

/**
 * Envelope + métricas do hop atual (ThreadLocal + MDC).
 */
public final class EcoEnvelopeHolder {

    public static final String MDC_TRACE = "trace_id";
    public static final String MDC_SPAN = "span_id";
    public static final String MDC_TRACE_ALIAS = "traceId";
    public static final String MDC_SPAN_ALIAS = "spanId";

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private EcoEnvelopeHolder() {
    }

    public static void bind(EcoEnvelope envelope) {
        State s = new State();
        s.envelope = envelope;
        s.filterStartNanos = System.nanoTime();
        STATE.set(s);
        applyMdc(envelope);
    }

    public static Optional<EcoEnvelope> current() {
        State s = STATE.get();
        return s == null ? Optional.empty() : Optional.ofNullable(s.envelope);
    }

    public static EcoEnvelope require() {
        return current().orElseThrow(() -> new IllegalStateException("envelope ausente"));
    }

    public static void replace(EcoEnvelope envelope) {
        State s = STATE.get();
        if (s == null) {
            bind(envelope);
            return;
        }
        s.envelope = envelope;
        applyMdc(envelope);
    }

    public static void bindUser(Long usuarioId) {
        current().ifPresent(env -> {
            if (env.getUserId() == null || env.getUserId().isBlank()) {
                replace(env.toBuilder().userId(EcoUlid.user(usuarioId)).build());
            }
        });
    }

    public static void markWorkStart() {
        State s = STATE.get();
        if (s != null && s.workStartNanos == 0L) {
            s.workStartNanos = System.nanoTime();
        }
    }

    public static void recordToolMs(long ms) {
        State s = STATE.get();
        if (s != null) {
            s.toolMs = ms;
        }
    }

    public static void executionPath(EcoExecutionPath path) {
        State s = STATE.get();
        if (s != null && path != null) {
            s.path = path;
        }
    }

    public static void outcome(String outcome) {
        State s = STATE.get();
        if (s != null && outcome != null) {
            s.outcome = outcome;
        }
    }

    public static long queueMs() {
        State s = STATE.get();
        if (s == null) {
            return 0L;
        }
        long end = s.workStartNanos != 0L ? s.workStartNanos : System.nanoTime();
        return Math.max(0L, (end - s.filterStartNanos) / 1_000_000L);
    }

    public static long totalMs() {
        State s = STATE.get();
        if (s == null) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - s.filterStartNanos) / 1_000_000L);
    }

    public static Long toolMs() {
        State s = STATE.get();
        return s == null ? null : s.toolMs;
    }

    public static EcoExecutionPath path() {
        State s = STATE.get();
        return s == null ? null : s.path;
    }

    public static String outcome() {
        State s = STATE.get();
        return s == null || s.outcome == null ? "SUCCESS" : s.outcome;
    }

    public static void authScheme(String scheme) {
        State s = STATE.get();
        if (s != null && scheme != null) {
            s.authScheme = scheme;
        }
    }

    public static void envelopeSynthesized(boolean value) {
        State s = STATE.get();
        if (s != null) {
            s.envelopeSynthesized = value;
        }
    }

    public static String authScheme() {
        State s = STATE.get();
        return s == null ? null : s.authScheme;
    }

    public static boolean envelopeSynthesized() {
        State s = STATE.get();
        return s != null && s.envelopeSynthesized;
    }

    public static void clear() {
        STATE.remove();
        MDC.remove(MDC_TRACE);
        MDC.remove(MDC_SPAN);
        MDC.remove(MDC_TRACE_ALIAS);
        MDC.remove(MDC_SPAN_ALIAS);
        MDC.remove("parent_span_id");
        MDC.remove("tool_call_id");
        MDC.remove("conversation_id");
        MDC.remove("task_id");
        MDC.remove("application_id");
        MDC.remove("context_id");
    }

    private static void applyMdc(EcoEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        put(MDC_TRACE, envelope.getTraceId());
        put(MDC_SPAN, envelope.getSpanId());
        put(MDC_TRACE_ALIAS, envelope.getTraceId());
        put(MDC_SPAN_ALIAS, envelope.getSpanId());
        put("parent_span_id", envelope.getParentSpanId());
        put("tool_call_id", envelope.getToolCallId());
        put("conversation_id", envelope.getConversationId());
        put("task_id", envelope.getTaskId());
        put("application_id", envelope.getAppCurrent());
    }

    private static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private static final class State {
        EcoEnvelope envelope;
        long filterStartNanos;
        long workStartNanos;
        Long toolMs;
        EcoExecutionPath path;
        String outcome;
        String authScheme;
        boolean envelopeSynthesized;
    }
}
