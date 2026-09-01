package com.consumoesperto.edith;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class CognitiveRequest {
    Long usuarioId;
    String conversationId;
    String content;
    String sourceAction;
    String clientRequestId;
    Map<String, Object> metadata;
    boolean awaitCompletion;
}
