package com.consumoesperto.service.ai;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AiStructuredOutputResult<T> {

    AiStructuredOutputStatus status;
    T payload;
    List<String> errors;
    String rawJson;
    int attempts;
    boolean requiresUserConfirmation;

    public boolean isValid() {
        return status == AiStructuredOutputStatus.VALID || status == AiStructuredOutputStatus.CORRECTED;
    }

    public boolean isRejected() {
        return status == AiStructuredOutputStatus.REJECTED || status == AiStructuredOutputStatus.NEEDS_CONFIRMATION;
    }
}
