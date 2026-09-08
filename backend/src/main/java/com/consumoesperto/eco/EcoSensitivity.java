package com.consumoesperto.eco;

public enum EcoSensitivity {
    PUBLIC,
    INTERNAL,
    PERSONAL,
    FINANCIAL;

    public static EcoSensitivity parse(String raw, EcoSensitivity fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return EcoSensitivity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Sensitivity só sobe. */
    public EcoSensitivity raiseTo(EcoSensitivity other) {
        if (other == null) {
            return this;
        }
        return other.ordinal() > this.ordinal() ? other : this;
    }
}
