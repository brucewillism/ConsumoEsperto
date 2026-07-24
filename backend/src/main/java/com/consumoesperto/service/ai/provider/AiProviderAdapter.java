package com.consumoesperto.service.ai.provider;

import com.consumoesperto.service.AiProviderType;
import com.consumoesperto.service.ai.AiRouterCapability;

/**
 * Contrato para novos provedores (Mistral, Qwen, Cohere, etc.) sem alterar o roteador.
 * Implementações concretas delegam HTTP; o roteador depende apenas desta interface.
 */
public interface AiProviderAdapter {

    /** Identificador estável (ex.: GROQ, MISTRAL, QWEN). */
    String providerId();

    AiProviderType providerType();

    boolean isEnabled();

    boolean supports(AiRouterCapability capability);

    /** Custo estimado USD por 1M tokens (entrada, saída). */
    default double[] costPerMillionTokens() {
        return new double[] {1.0, 3.0};
    }
}
