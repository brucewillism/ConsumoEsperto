package com.consumoesperto.service.ai.analytics;

import com.consumoesperto.dto.ai.AiTraceFilterDTO;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.service.ai.AITaskType;
import com.consumoesperto.service.ai.trace.AiTraceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiRouterAdminService {

    private final AiTraceStore traceStore;
    private final AiPerformanceAnalyticsService performanceAnalytics;
    private final AiQualityScoreService qualityScoreService;
    private final AiRouterRecommendationService recommendationService;
    private final AiRouterAlertService alertService;

    public Map<String, Object> dashboard(AiTraceFilterDTO filter) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("coletadoEm", Instant.now().toString());
        out.put("tracesArmazenados", traceStore.size());
        out.put("performance", performanceAnalytics.aggregate(filter));
        out.put("qualityScores", qualityScoreService.scores(filter));
        out.put("recomendacoes", recommendationService.generate());
        out.put("alertas", alertService.listEvents(20));
        return out;
    }

    public List<Map<String, Object>> traces(AiTraceFilterDTO filter, int limit, int offset) {
        return traceStore.find(filter, limit, offset).stream()
            .map(this::traceToMap)
            .collect(Collectors.toList());
    }

    public Map<String, Object> models(AiTraceFilterDTO filter) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modelos", performanceAnalytics.aggregate(filter).get("porModelo"));
        out.put("qualityScores", qualityScoreService.scores(filter));
        return out;
    }

    public List<Map<String, Object>> recommendations() {
        return recommendationService.generate();
    }

    private Map<String, Object> traceToMap(AiTrace t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", t.getTraceId());
        m.put("userId", t.getUserId());
        m.put("taskType", t.getTaskType() != null ? t.getTaskType().name() : null);
        m.put("modeloEscolhido", t.getModeloEscolhido());
        m.put("modeloPreferencial", t.getModeloPreferencial());
        m.put("modeloFallback", t.getModeloFallback());
        m.put("inicioExecucao", t.getInicioExecucao() != null ? t.getInicioExecucao().toString() : null);
        m.put("fimExecucao", t.getFimExecucao() != null ? t.getFimExecucao().toString() : null);
        m.put("duracaoMs", t.getDuracaoMs());
        m.put("tokensEntrada", t.getTokensEntrada());
        m.put("tokensSaida", t.getTokensSaida());
        m.put("custoEstimadoUsd", t.getCustoEstimadoUsd());
        m.put("temperatura", t.getTemperatura());
        m.put("tentativas", t.getTentativas());
        m.put("tentativasModelos", t.getTentativasModelos());
        m.put("fallbackUtilizado", t.isFallbackUtilizado());
        m.put("structuredOutputValido", t.getStructuredOutputValido());
        m.put("structuredOutputCorrigido", t.getStructuredOutputCorrigido());
        m.put("status", t.getStatus() != null ? t.getStatus().name() : null);
        m.put("erro", t.getErro());
        return m;
    }

    public static AiTraceFilterDTO filterFromParams(
        Instant desde,
        Instant ate,
        String modelo,
        AITaskType taskType,
        Long userId,
        com.consumoesperto.model.ai.AiTraceStatus status,
        Boolean fallback
    ) {
        return AiTraceFilterDTO.builder()
            .desde(desde)
            .ate(ate)
            .modelo(modelo)
            .taskType(taskType)
            .userId(userId)
            .status(status)
            .fallback(fallback)
            .build();
    }
}
