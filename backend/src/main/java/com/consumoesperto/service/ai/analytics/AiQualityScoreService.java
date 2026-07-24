package com.consumoesperto.service.ai.analytics;

import com.consumoesperto.dto.ai.AiTraceFilterDTO;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.service.ai.trace.AiTraceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Índice de qualidade por modelo — apenas indicador interno; nunca altera roteamento.
 */
@Service
@RequiredArgsConstructor
public class AiQualityScoreService {

    private static final double LATENCY_BASELINE_MS = 2_000.0;

    private final AiTraceStore traceStore;
    private final AiPerformanceAnalyticsService analytics;

    public List<Map<String, Object>> scores(AiTraceFilterDTO filter) {
        List<AiTrace> traces = traceStore.find(filter, 10_000, 0);
        Map<String, AiPerformanceAnalyticsService.ModelStats> byModel = new LinkedHashMap<>();
        for (AiTrace t : traces) {
            String m = t.getModeloEscolhido();
            if (m == null || m.isBlank()) {
                continue;
            }
            byModel.computeIfAbsent(m, AiPerformanceAnalyticsService.ModelStats::new).accept(t);
        }

        double maxCost = byModel.values().stream()
            .mapToDouble(AiPerformanceAnalyticsService.ModelStats::avgCostUsd)
            .max()
            .orElse(0.001);

        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : byModel.entrySet()) {
            out.add(scoreEntry(e.getKey(), e.getValue(), maxCost));
        }
        out.sort(Comparator.<Map<String, Object>>comparingDouble(m -> ((Number) m.get("scoreFinal")).doubleValue()).reversed());
        return out;
    }

    public List<Map<String, Object>> scoresLastDays(int days) {
        Instant desde = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return scores(AiTraceFilterDTO.builder().desde(desde).build());
    }

    private Map<String, Object> scoreEntry(
        String modelo,
        AiPerformanceAnalyticsService.ModelStats stats,
        double maxCost
    ) {
        double velocidade = clampScore(100.0 - (stats.avgLatencyMs() / LATENCY_BASELINE_MS) * 40.0);
        double sucesso = clampScore(stats.successRate() * 100.0);
        double fallback = clampScore((1.0 - stats.fallbackRate()) * 100.0);
        double custo = maxCost <= 0
            ? 100.0
            : clampScore(100.0 - (stats.avgCostUsd() / maxCost) * 60.0);
        double qualidade = stats.structuredInvalidRate() > 0
            ? clampScore((1.0 - stats.structuredInvalidRate()) * 100.0)
            : sucesso;

        double scoreFinal = round1((velocidade + sucesso + fallback + custo + qualidade) / 5.0);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modelo", modelo);
        m.put("velocidade", round1(velocidade));
        m.put("sucesso", round1(sucesso));
        m.put("fallback", round1(fallback));
        m.put("custo", round1(custo));
        m.put("qualidade", round1(qualidade));
        m.put("scoreFinal", scoreFinal);
        m.put("amostras", stats.getTotal());
        return m;
    }

    private static double clampScore(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
