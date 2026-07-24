package com.consumoesperto.service.ai;

import com.consumoesperto.config.AiRouterProperties;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.model.ai.AiTraceStatus;
import com.consumoesperto.service.AiProviderType;
import com.consumoesperto.service.ai.analytics.AiPerformanceAnalyticsService;
import com.consumoesperto.service.ai.analytics.AiQualityScoreService;
import com.consumoesperto.service.ai.analytics.AiRouterAlertService;
import com.consumoesperto.service.ai.trace.AiTraceService;
import com.consumoesperto.service.ai.trace.AiTraceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiRouterServiceTest {

    private AiRouterService router;
    private AiRouterMetrics metrics;
    private AiTraceStore traceStore;

    @BeforeEach
    void setUp() {
        metrics = new AiRouterMetrics();
        traceStore = new AiTraceStore();
        AiRouterProperties props = new AiRouterProperties();
        props.setEnabled(true);
        props.getClaude().setEnabled(true);
        props.getGroq().setEnabled(true);
        props.getOpenai().setEnabled(true);
        props.getOllama().setEnabled(false);
        AiTraceService traceService = new AiTraceService(
            traceStore,
            props,
            new AiRouterAlertService(props, new AiPerformanceAnalyticsService(traceStore), mock(com.consumoesperto.service.AlertaOperacionalService.class))
        );
        router = new AiRouterService(props, metrics, traceService);
    }

    @Test
    void whatsappCommandNuncaIncluiClaude() {
        List<AiProviderType> chain = router.resolveChain(AITaskType.WHATSAPP_COMMAND);
        assertEquals(List.of(AiProviderType.GROQ, AiProviderType.DEEPSEEK, AiProviderType.OPENAI), chain);
        assertFalse(chain.contains(AiProviderType.CLAUDE));
    }

    @Test
    void structuredOutputPriorizaGpt() {
        List<AiProviderType> chain = router.resolveChain(AITaskType.STRUCTURED_OUTPUT);
        assertEquals(AiProviderType.OPENAI, chain.get(0));
    }

    @Test
    void ocrReceiptPriorizaGemini() {
        List<AiProviderType> chain = router.resolveChain(AITaskType.OCR_RECEIPT);
        assertEquals(AiProviderType.GEMINI, chain.get(0));
    }

    @Test
    void desabilitarProvedorRemoveDaCadeia() {
        AiRouterProperties props = new AiRouterProperties();
        props.getGroq().setEnabled(false);
        props.getDeepseek().setEnabled(false);
        AiTraceService traceService = new AiTraceService(
            traceStore,
            props,
            new AiRouterAlertService(props, new AiPerformanceAnalyticsService(traceStore), mock(com.consumoesperto.service.AlertaOperacionalService.class))
        );
        AiRouterService r = new AiRouterService(props, metrics, traceService);
        List<AiProviderType> chain = r.resolveChain(AITaskType.WHATSAPP_COMMAND);
        assertEquals(List.of(AiProviderType.OPENAI), chain);
    }

    @Test
    void ollamaSoComFallbackEmergencia() {
        AiRouterProperties props = new AiRouterProperties();
        props.setOllamaEmergencyFallback(true);
        props.getOllama().setEnabled(true);
        AiTraceService traceService = new AiTraceService(
            traceStore,
            props,
            new AiRouterAlertService(props, new AiPerformanceAnalyticsService(traceStore), mock(com.consumoesperto.service.AlertaOperacionalService.class))
        );
        AiRouterService r = new AiRouterService(props, metrics, traceService);
        List<AiProviderType> chain = r.resolveChain(AITaskType.CHAT);
        assertTrue(chain.contains(AiProviderType.OLLAMA));
        assertEquals(AiProviderType.OLLAMA, chain.get(chain.size() - 1));
    }

    @Test
    void registraTraceAposSucesso() {
        router.route(
            AiRouterRequestContext.of(1L, 0.0),
            AITaskType.WHATSAPP_COMMAND,
            null,
            p -> p == AiProviderType.GROQ,
            "teste",
            (p, c) -> "{\"ok\":true}",
            "erro: "
        );
        assertEquals(1, traceStore.size());
        AiTrace t = traceStore.find(null, 1, 0).get(0);
        assertEquals(AiTraceStatus.SUCCESS, t.getStatus());
        assertEquals("Groq", t.getModeloEscolhido());
    }

    @Test
    void qualityScoreCalculaIndicadores() {
        traceStore.add(sampleTrace("Groq", 400, true));
        traceStore.add(sampleTrace("Groq", 300, true));
        AiQualityScoreService qs = new AiQualityScoreService(traceStore, new AiPerformanceAnalyticsService(traceStore));
        var scores = qs.scores(null);
        assertFalse(scores.isEmpty());
        assertTrue(((Number) scores.get(0).get("scoreFinal")).doubleValue() > 0);
    }

    private static AiTrace sampleTrace(String modelo, long ms, boolean ok) {
        return AiTrace.builder()
            .traceId(java.util.UUID.randomUUID().toString())
            .userId(1L)
            .taskType(AITaskType.WHATSAPP_COMMAND)
            .modeloEscolhido(modelo)
            .modeloPreferencial(modelo)
            .inicioExecucao(Instant.now().minusMillis(ms))
            .fimExecucao(Instant.now())
            .duracaoMs(ms)
            .tokensEntrada(50)
            .tokensSaida(20)
            .custoEstimadoUsd(0.00001)
            .tentativas(1)
            .fallbackUtilizado(false)
            .structuredOutputValido(ok)
            .status(AiTraceStatus.SUCCESS)
            .build();
    }
}
