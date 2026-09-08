package com.consumoesperto.edith;

import com.consumoesperto.eco.EcoEnvelopeHolder;
import com.consumoesperto.eco.EcoUlid;
import org.slf4j.MDC;

/**
 * Compatibilidade com o MDC cognitivo. O envelope canônico vive em {@link EcoEnvelopeHolder}.
 */
public final class EdithTraceContext {

    public static final String TRACE_ID = EcoEnvelopeHolder.MDC_TRACE;
    public static final String APPLICATION_ID = "application_id";
    public static final String CONVERSATION_ID = "conversation_id";
    public static final String TASK_ID = "task_id";
    public static final String CONTEXT_ID = "context_id";
    public static final String TOOL_CALL_ID = "tool_call_id";

    private EdithTraceContext() {}

    public static String createOrReuse(String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return EcoEnvelopeHolder.current()
            .map(e -> e.getTraceId())
            .filter(t -> t != null && !t.isBlank())
            .orElseGet(EcoUlid::trace);
    }

    public static void put(
        String traceId,
        String applicationId,
        String conversationId,
        String taskId,
        String contextId,
        String toolCallId
    ) {
        putIfPresent(TRACE_ID, traceId);
        putIfPresent(EcoEnvelopeHolder.MDC_TRACE_ALIAS, traceId);
        putIfPresent(APPLICATION_ID, applicationId);
        putIfPresent(CONVERSATION_ID, conversationId);
        putIfPresent(TASK_ID, taskId);
        putIfPresent(CONTEXT_ID, contextId);
        putIfPresent(TOOL_CALL_ID, toolCallId);
        EcoEnvelopeHolder.current().ifPresent(env -> {
            EcoEnvelopeHolder.replace(env.toBuilder()
                .traceId(traceId != null ? traceId : env.getTraceId())
                .conversationId(conversationId != null ? conversationId : env.getConversationId())
                .taskId(taskId != null ? taskId : env.getTaskId())
                .toolCallId(toolCallId != null ? toolCallId : env.getToolCallId())
                .build());
        });
    }

    public static void clear() {
        if (EcoEnvelopeHolder.current().isPresent()) {
            return;
        }
        MDC.remove(TRACE_ID);
        MDC.remove(EcoEnvelopeHolder.MDC_TRACE_ALIAS);
        MDC.remove(APPLICATION_ID);
        MDC.remove(CONVERSATION_ID);
        MDC.remove(TASK_ID);
        MDC.remove(CONTEXT_ID);
        MDC.remove(TOOL_CALL_ID);
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
