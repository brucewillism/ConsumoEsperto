package com.consumoesperto.eco;

public final class EcoHeaders {

    public static final String TRACE_ID = "X-Eco-Trace-Id";
    public static final String SPAN_ID = "X-Eco-Span-Id";
    public static final String PARENT_SPAN_ID = "X-Eco-Parent-Span-Id";
    public static final String USER_ID = "X-Eco-User-Id";
    public static final String APP_ORIGIN = "X-Eco-App-Origin";
    public static final String APP_CURRENT = "X-Eco-App-Current";
    public static final String CONVERSATION_ID = "X-Eco-Conversation-Id";
    public static final String TASK_ID = "X-Eco-Task-Id";
    public static final String TOOL_CALL_ID = "X-Eco-Tool-Call-Id";
    public static final String MODE = "X-Eco-Mode";
    public static final String DEADLINE = "X-Eco-Deadline";
    public static final String DEADLINE_TOTAL = "X-Eco-Deadline-Total";
    public static final String DEADLINE_FIRST_TOKEN = "X-Eco-Deadline-First-Token";
    public static final String SENSITIVITY = "X-Eco-Sensitivity";
    public static final String SERVICE_KEY = "X-Eco-Service-Key";

    public static final String APP_CONSUMO = "consumo-esperto";
    public static final String APP_JARVIS = "jarvis";
    public static final String APP_EDITH = "edith";

    private EcoHeaders() {
    }
}
