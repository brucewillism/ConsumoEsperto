package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Roteamento cognitivo gradual do J.A.R.V.I.S. (WhatsApp/Evolution) para E.D.I.T.H.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EdithJarvisRoutingService {

    private final EdithProperties edithProperties;
    private final EdithIntegrationService edithIntegrationService;
    private final CognitiveGatewaySelector gatewaySelector;

    /**
     * Comandos cognitivos (não resolvidos pelo fast path) podem ser enviados à E.D.I.T.H.
     */
    public Optional<String> tryCognitiveReply(Long usuarioId, String text) {
        if (!edithProperties.isEnabled() || !edithIntegrationService.isOperational()) {
            return Optional.empty();
        }
        if (text == null || text.isBlank() || text.length() < 12) {
            return Optional.empty();
        }
        try {
            CognitiveResponse response = gatewaySelector.active().send(CognitiveRequest.builder()
                .usuarioId(usuarioId)
                .content(text)
                .sourceAction("consumo.chat")
                .awaitCompletion(true)
                .build());
            return Optional.ofNullable(response.getResultText());
        } catch (EdithException e) {
            log.warn("edith_jarvis_route_failed code={}", e.getCode());
            return Optional.empty();
        }
    }
}
