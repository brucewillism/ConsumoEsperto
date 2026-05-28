package com.consumoesperto.service.ai;

/**
 * Estratégias do AI Token Suppressor (ATS). {@link #AUTO} resolve via {@link AiStrategySelector}.
 */
public enum AiGatewayStrategy {
    FAST("fast"),
    BALANCED("balanced"),
    CODE_FOCUSED("code-focused"),
    ULTRA("ultra");

    private final String atsValue;

    AiGatewayStrategy(String atsValue) {
        this.atsValue = atsValue;
    }

    public String atsValue() {
        return atsValue;
    }

    public static AiGatewayStrategy fromAtsValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return BALANCED;
        }
        String s = raw.trim().toLowerCase();
        for (AiGatewayStrategy v : values()) {
            if (v.atsValue.equals(s)) {
                return v;
            }
        }
        return BALANCED;
    }
}
