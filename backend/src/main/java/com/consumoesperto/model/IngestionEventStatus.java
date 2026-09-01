package com.consumoesperto.model;

public enum IngestionEventStatus {
    RECEIVED,
    PARSED,
    NEEDS_REVIEW,
    PENDING_RECONCILIATION,
    REGISTERED,
    DUPLICATE,
    REJECTED,
    ERROR
}
