package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolve o gateway cognitivo: Edith quando operacional, senão legado se o fallback estiver ligado.
 */
@Component
@Slf4j
public class CognitiveGatewaySelector {

    private final EdithProperties properties;
    private final CognitiveGateway legacyGateway;
    private final CognitiveGateway edithGateway;
    private final EdithIntegrationService integrationService;

    public CognitiveGatewaySelector(
        EdithProperties properties,
        LegacyCognitiveGateway legacyGateway,
        EdithCognitiveGateway edithGateway,
        EdithIntegrationService integrationService
    ) {
        this.properties = properties;
        this.legacyGateway = legacyGateway;
        this.edithGateway = edithGateway;
        this.integrationService = integrationService;
    }

    /** Gateway preferido pela flag (sem fallback). Preferir {@link #dispatch(CognitiveRequest)}. */
    public boolean usesEdith() {
        return properties.isEnabled() && integrationService.isOperational();
    }

    public CognitiveGateway active() {
        if (properties.isEnabled() && integrationService.isOperational()) {
            return edithGateway;
        }
        return legacyGateway;
    }

    public CognitiveResponse dispatch(CognitiveRequest request) {
        String traceId = EdithTraceContext.createOrReuse(request.getTraceId());
        CognitiveRequest traced = CognitiveRequest.builder()
            .usuarioId(request.getUsuarioId())
            .conversationId(request.getConversationId())
            .content(request.getContent())
            .sourceAction(request.getSourceAction())
            .clientRequestId(request.getClientRequestId())
            .metadata(request.getMetadata())
            .awaitCompletion(request.isAwaitCompletion())
            .applicationId(request.getApplicationId())
            .screen(request.getScreen())
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .traceId(traceId)
            .capability(request.getCapability())
            .build();

        if (properties.isEnabled() && integrationService.isOperational()) {
            try {
                CognitiveResponse edith = edithGateway.send(traced);
                return withMode(edith, "EDITH", traceId);
            } catch (EdithException e) {
                log.warn("edith_gateway_failed code={} fallback={}", e.getCode(), properties.isFallbackEnabled());
                if (properties.isFallbackEnabled()) {
                    return withMode(legacyGateway.send(traced), "LEGACY", traceId);
                }
                return CognitiveResponse.builder()
                    .status("FAILED")
                    .mode("DEGRADED")
                    .traceId(traceId)
                    .resultText("O assistente cognitivo está temporariamente indisponível. Suas finanças continuam acessíveis no app.")
                    .build();
            }
        }

        if (!properties.isEnabled() || properties.isFallbackEnabled()) {
            return withMode(legacyGateway.send(traced), properties.isEnabled() ? "LEGACY" : "LOCAL", traceId);
        }

        return CognitiveResponse.builder()
            .status("FAILED")
            .mode("DEGRADED")
            .traceId(traceId)
            .resultText("O assistente cognitivo está temporariamente indisponível. Suas finanças continuam acessíveis no app.")
            .build();
    }

    private static CognitiveResponse withMode(CognitiveResponse response, String mode, String traceId) {
        if (response == null) {
            return CognitiveResponse.builder().mode(mode).traceId(traceId).status("FAILED").build();
        }
        return CognitiveResponse.builder()
            .conversationId(response.getConversationId())
            .messageId(response.getMessageId())
            .taskId(response.getTaskId())
            .requestId(response.getRequestId())
            .clientRequestId(response.getClientRequestId())
            .contextRef(response.getContextRef())
            .status(response.getStatus())
            .resultText(response.getResultText())
            .mode(mode)
            .traceId(traceId != null ? traceId : response.getTraceId())
            .build();
    }
}
