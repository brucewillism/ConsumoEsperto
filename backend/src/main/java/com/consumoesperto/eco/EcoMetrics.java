package com.consumoesperto.eco;

import lombok.extern.slf4j.Slf4j;

/**
 * Log canônico da seção 5 — uma linha por hop, grepável por {@code trace_id}.
 */
@Slf4j
public final class EcoMetrics {

    private EcoMetrics() {
    }

    public static void logHop() {
        EcoEnvelope env = EcoEnvelopeHolder.current().orElse(null);
        String trace = env != null ? env.getTraceId() : "-";
        String span = env != null ? env.getSpanId() : "-";
        String path = EcoEnvelopeHolder.path() != null ? EcoEnvelopeHolder.path().name() : "-";
        Long toolMs = EcoEnvelopeHolder.toolMs();
        StringBuilder sb = new StringBuilder("eco_span");
        sb.append(" trace_id=").append(trace);
        sb.append(" span_id=").append(span);
        if (env != null && env.getParentSpanId() != null) {
            sb.append(" parent_span_id=").append(env.getParentSpanId());
        }
        sb.append(" outcome=").append(EcoEnvelopeHolder.outcome());
        sb.append(" execution_path=").append(path);
        sb.append(" t_total_ms=").append(EcoEnvelopeHolder.totalMs());
        sb.append(" t_queue_ms=").append(EcoEnvelopeHolder.queueMs());
        if (toolMs != null) {
            sb.append(" t_tool_ms=").append(toolMs);
        }
        long unaccounted = EcoEnvelopeHolder.totalMs() - EcoEnvelopeHolder.queueMs() - (toolMs != null ? toolMs : 0L);
        sb.append(" t_unaccounted_ms=").append(Math.max(0L, unaccounted));
        if (EcoEnvelopeHolder.envelopeSynthesized()) {
            sb.append(" envelope_synthesized=true");
        }
        if (EcoEnvelopeHolder.authScheme() != null) {
            sb.append(" auth_scheme=").append(EcoEnvelopeHolder.authScheme());
        }
        if (env != null && env.getMode() != null) {
            sb.append(" mode=").append(env.getMode().name());
        }
        log.info(sb.toString());
    }
}
