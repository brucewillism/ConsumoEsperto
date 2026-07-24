package com.consumoesperto.dto.ai;

import com.consumoesperto.model.ai.AiTraceStatus;
import com.consumoesperto.service.ai.AITaskType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AiTraceFilterDTO {

    private final Instant desde;
    private final Instant ate;
    private final String modelo;
    private final AITaskType taskType;
    private final Long userId;
    private final AiTraceStatus status;
    private final Boolean fallback;
}
