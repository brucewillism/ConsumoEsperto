package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import org.springframework.stereotype.Component;

/**
 * Resolve o gateway cognitivo ativo com base na feature flag — sem fallback automático entre implementações.
 */
@Component
public class CognitiveGatewaySelector {

    private final EdithProperties properties;
    private final CognitiveGateway legacyGateway;
    private final CognitiveGateway edithGateway;

    public CognitiveGatewaySelector(
        EdithProperties properties,
        LegacyCognitiveGateway legacyGateway,
        EdithCognitiveGateway edithGateway
    ) {
        this.properties = properties;
        this.legacyGateway = legacyGateway;
        this.edithGateway = edithGateway;
    }

    public CognitiveGateway active() {
        if (properties.isEnabled()) {
            return edithGateway;
        }
        return legacyGateway;
    }
}
