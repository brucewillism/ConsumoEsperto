package com.consumoesperto.autonomy;

/**
 * Decisão da policy: aplicar, só sugerir, rever, ou proibir.
 */
public enum PolicyOutcome {
    APPLY,
    SUGGEST,
    REVIEW,
    FORBIDDEN
}
