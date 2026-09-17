package com.consumoesperto.autonomy;

public enum OutboxProcessingStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}
