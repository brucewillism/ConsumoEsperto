package com.consumoesperto.service.ai.trace;

import com.consumoesperto.model.ai.AiTrace;

import java.util.Optional;

/** Contexto de trace da chamada IA em curso (mesma thread). */
public final class AiTraceHolder {

    private static final ThreadLocal<AiTrace.Mutable> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_COMPLETED_TRACE_ID = new ThreadLocal<>();

    private AiTraceHolder() {
    }

    public static void set(AiTrace.Mutable trace) {
        CURRENT.set(trace);
    }

    public static Optional<AiTrace.Mutable> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<String> lastCompletedTraceId() {
        return Optional.ofNullable(LAST_COMPLETED_TRACE_ID.get());
    }

    public static void markCompleted(String traceId) {
        LAST_COMPLETED_TRACE_ID.set(traceId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void clearCompleted() {
        LAST_COMPLETED_TRACE_ID.remove();
    }
}
