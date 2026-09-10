package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitiveGatewaySelectorTest {

    @Mock EdithProperties properties;
    @Mock LegacyCognitiveGateway legacy;
    @Mock EdithCognitiveGateway edith;
    @Mock EdithIntegrationService integration;

    @Test
    void edithFalhaComFallbackUsaLegacy() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isFallbackEnabled()).thenReturn(true);
        when(integration.isOperational()).thenReturn(true);
        when(edith.send(any())).thenThrow(new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "down"));
        when(legacy.send(any())).thenReturn(CognitiveResponse.builder().resultText("ok-local").status("COMPLETED").build());

        CognitiveGatewaySelector selector = new CognitiveGatewaySelector(properties, legacy, edith, integration);
        CognitiveResponse r = selector.dispatch(CognitiveRequest.builder().usuarioId(1L).content("oi").build());
        assertEquals("LEGACY", r.getMode());
        assertEquals("ok-local", r.getResultText());
    }

    @Test
    void edithFalhaSemFallbackDegradaSemLegacy() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isFallbackEnabled()).thenReturn(false);
        when(integration.isOperational()).thenReturn(true);
        when(edith.send(any())).thenThrow(new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "down"));

        CognitiveGatewaySelector selector = new CognitiveGatewaySelector(properties, legacy, edith, integration);
        CognitiveResponse r = selector.dispatch(CognitiveRequest.builder().usuarioId(1L).content("oi").build());
        assertEquals("DEGRADED", r.getMode());
        assertEquals("O assistente cognitivo está temporariamente indisponível. Suas finanças continuam acessíveis no app.", r.getResultText());
        verify(legacy, never()).send(any());
    }

    @Test
    void circuitAbertoUsaLegacySeFallbackLigado() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isFallbackEnabled()).thenReturn(true);
        when(integration.isOperational()).thenReturn(false);
        when(legacy.send(any())).thenReturn(CognitiveResponse.builder().resultText("ok-local").status("COMPLETED").build());

        CognitiveGatewaySelector selector = new CognitiveGatewaySelector(properties, legacy, edith, integration);
        CognitiveResponse r = selector.dispatch(CognitiveRequest.builder().usuarioId(1L).content("oi").build());
        assertEquals("LEGACY", r.getMode());
        verify(edith, never()).send(any());
    }

    @Test
    void flagOffUsaLegacy() {
        when(properties.isEnabled()).thenReturn(false);
        when(legacy.send(any())).thenReturn(CognitiveResponse.builder().resultText("legado").status("COMPLETED").build());

        CognitiveGatewaySelector selector = new CognitiveGatewaySelector(properties, legacy, edith, integration);
        CognitiveResponse r = selector.dispatch(CognitiveRequest.builder().usuarioId(1L).content("oi").build());
        assertEquals("LOCAL", r.getMode());
        verify(edith, never()).send(any());
    }
}
