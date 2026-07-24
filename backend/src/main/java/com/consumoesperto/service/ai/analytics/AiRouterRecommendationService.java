package com.consumoesperto.service.ai.analytics;

import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.service.ai.AITaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recomendações informativas para o administrador — jamais altera configuração automaticamente.
 */
@Service
@RequiredArgsConstructor
public class AiRouterRecommendationService {

    private final AiPerformanceAnalyticsService analytics;

    public List<Map<String, Object>> generate() {
        Instant now = Instant.now();
        Instant seteDias = now.minus(7, ChronoUnit.DAYS);
        Instant quatorzeDias = now.minus(14, ChronoUnit.DAYS);

        List<AiTrace> recent = analytics.tracesBetween(seteDias, now);
        List<AiTrace> anterior = analytics.tracesBetween(quatorzeDias, seteDias);

        List<Map<String, Object>> out = new ArrayList<>();
        out.addAll(recommendSuccessDrop(recent, anterior));
        out.addAll(recommendOcrFallback(recent, anterior));
        out.addAll(recommendSimpleTaskOveruse(recent));
        out.addAll(recommendStructuredOutputCost(recent));
        return out;
    }

    private List<Map<String, Object>> recommendSuccessDrop(List<AiTrace> recent, List<AiTrace> anterior) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, AiPerformanceAnalyticsService.ModelStats> rec = groupByModel(recent);
        Map<String, AiPerformanceAnalyticsService.ModelStats> ant = groupByModel(anterior);

        for (var e : rec.entrySet()) {
            AiPerformanceAnalyticsService.ModelStats prev = ant.get(e.getKey());
            if (prev == null || prev.getTotal() < 10 || e.getValue().getTotal() < 10) {
                continue;
            }
            double prevRate = prev.successRate() * 100;
            double nowRate = e.getValue().successRate() * 100;
            if (prevRate - nowRate >= 10) {
                out.add(rec(
                    "QUEDA_SUCESSO",
                    String.format(
                        Locale.forLanguageTag("pt-PT"),
                        "%s apresentou queda de sucesso de %.0f%% para %.0f%% nos últimos sete dias.",
                        e.getKey(), prevRate, nowRate
                    ),
                    e.getKey(),
                    null
                ));
            }
        }
        return out;
    }

    private List<Map<String, Object>> recommendOcrFallback(List<AiTrace> recent, List<AiTrace> anterior) {
        List<Map<String, Object>> out = new ArrayList<>();
        double fbRecent = fallbackRateForTask(recent, AITaskType.OCR_INVOICE);
        double fbPrev = fallbackRateForTask(anterior, AITaskType.OCR_INVOICE);
        if (fbPrev > 0.05 && fbRecent < fbPrev * 0.6) {
            out.add(rec(
                "OCR_FALLBACK_MELHORIA",
                String.format(
                    Locale.forLanguageTag("pt-PT"),
                    "Fallbacks em OCR de faturas reduziram de %.0f%% para %.0f%% na última semana (prioridade Gemini).",
                    fbPrev * 100, fbRecent * 100
                ),
                "Gemini",
                AITaskType.OCR_INVOICE.name()
            ));
        }
        return out;
    }

    private List<Map<String, Object>> recommendSimpleTaskOveruse(List<AiTrace> recent) {
        List<Map<String, Object>> out = new ArrayList<>();
        long claudeSimple = recent.stream()
            .filter(t -> t.getTaskType() == AITaskType.WHATSAPP_COMMAND
                && "Claude".equalsIgnoreCase(nullSafe(t.getModeloEscolhido())))
            .count();
        long whatsappTotal = recent.stream()
            .filter(t -> t.getTaskType() == AITaskType.WHATSAPP_COMMAND)
            .count();
        if (whatsappTotal >= 20 && claudeSimple > whatsappTotal * 0.15) {
            out.add(rec(
                "MODELO_INADEQUADO",
                "Claude está a ser utilizado em excesso para tarefas simples (WHATSAPP_COMMAND). Considere rever a cadeia Groq → DeepSeek → GPT.",
                "Claude",
                AITaskType.WHATSAPP_COMMAND.name()
            ));
        }
        return out;
    }

    private List<Map<String, Object>> recommendStructuredOutputCost(List<AiTrace> recent) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, AiPerformanceAnalyticsService.ModelStats> byModel = new LinkedHashMap<>();
        for (AiTrace t : recent) {
            if (t.getTaskType() != AITaskType.STRUCTURED_OUTPUT) {
                continue;
            }
            String m = nullSafe(t.getModeloEscolhido());
            if (m.isBlank()) {
                continue;
            }
            byModel.computeIfAbsent(m, AiPerformanceAnalyticsService.ModelStats::new).accept(t);
        }
        String cheapest = null;
        double minCost = Double.MAX_VALUE;
        for (var e : byModel.entrySet()) {
            if (e.getValue().getTotal() >= 5 && e.getValue().avgCostUsd() < minCost) {
                minCost = e.getValue().avgCostUsd();
                cheapest = e.getKey();
            }
        }
        if (cheapest != null) {
            out.add(rec(
                "CUSTO_STRUCTURED",
                String.format(
                    Locale.forLanguageTag("pt-PT"),
                    "%s apresentou menor custo médio (US$ %.5f) em Structured Output nesta semana.",
                    cheapest, minCost
                ),
                cheapest,
                AITaskType.STRUCTURED_OUTPUT.name()
            ));
        }
        return out;
    }

    private static Map<String, AiPerformanceAnalyticsService.ModelStats> groupByModel(List<AiTrace> traces) {
        Map<String, AiPerformanceAnalyticsService.ModelStats> map = new LinkedHashMap<>();
        for (AiTrace t : traces) {
            String m = nullSafe(t.getModeloEscolhido());
            if (m.isBlank()) {
                continue;
            }
            map.computeIfAbsent(m, AiPerformanceAnalyticsService.ModelStats::new).accept(t);
        }
        return map;
    }

    private static double fallbackRateForTask(List<AiTrace> traces, AITaskType task) {
        long total = traces.stream().filter(t -> t.getTaskType() == task).count();
        if (total == 0) {
            return 0;
        }
        long fb = traces.stream().filter(t -> t.getTaskType() == task && t.isFallbackUtilizado()).count();
        return fb / (double) total;
    }

    private static Map<String, Object> rec(String tipo, String mensagem, String modelo, String taskType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("mensagem", mensagem);
        m.put("modelo", modelo);
        m.put("taskType", taskType);
        m.put("automatico", false);
        m.put("geradoEm", Instant.now().toString());
        return m;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
