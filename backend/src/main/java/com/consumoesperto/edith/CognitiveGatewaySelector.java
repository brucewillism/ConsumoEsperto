package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import org.springframework.beans.factory.annotation.Autowired;
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
        @Autowired(required = false) LegacyCognitiveGateway legacyGateway,
        @Autowired(required = false) EdithCognitiveGateway edithGateway
    ) {
        this.properties = properties;
        this.legacyGateway = legacyGateway;
        this.edithGateway = edithGateway;
    }

    public CognitiveGateway active() {
        if (properties.isEnabled()) {
            if (edithGateway == null) {
                throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "E.D.I.T.H. indisponível");
            }
            return edithGateway;
        }
        if (legacyGateway == null) {
            throw new EdithException(EdithErrorCode.EDITH_DISABLED, "Gateway legado indisponível");
        }
        return legacyGateway;
    }
}
