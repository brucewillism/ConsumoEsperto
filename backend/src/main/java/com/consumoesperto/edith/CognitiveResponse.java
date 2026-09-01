package com.consumoesperto.edith;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CognitiveResponse {
    String conversationId;
    String messageId;
    String taskId;
    String requestId;
    String clientRequestId;
    String contextRef;
    String status;
    String resultText;
}
