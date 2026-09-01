package com.consumoesperto.edith;

/**
 * Fronteira cognitiva — seleção explícita via configuração (sem fallback oculto).
 */
public interface CognitiveGateway {

    boolean isActive();

    CognitiveResponse send(CognitiveRequest request);
}
