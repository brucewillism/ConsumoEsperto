package com.consumoesperto.eco;

import java.time.Duration;

public enum EcoMode {
    INTERACTIVE(Duration.ofMillis(2000)),
    BALANCED(Duration.ofMillis(8000)),
    DEEP(Duration.ofMillis(60000));

    private final Duration defaultBudget;

    EcoMode(Duration defaultBudget) {
        this.defaultBudget = defaultBudget;
    }

    public Duration defaultBudget() {
        return defaultBudget;
    }

    public static EcoMode parse(String raw, EcoMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return EcoMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
