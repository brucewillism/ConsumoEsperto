package com.consumoesperto.service.ai.analytics;

import com.consumoesperto.dto.ai.AiTraceFilterDTO;
import com.consumoesperto.model.ai.AiTrace;
import com.consumoesperto.model.ai.AiTraceStatus;
import com.consumoesperto.service.ai.AITaskType;
import com.consumoesperto.service.ai.trace.AiTraceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiPerformanceAnalyticsService {

    private final AiTraceStore traceStore;

    public Map<String, Object> aggregate(AiTraceFilterDTO filter) {
        List<AiTrace> traces = traceStore.find(filter, 10_000, 0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalChamadas", traces.size());
        out.put("porModelo", aggregateByModel(traces));
        out.put("porTarefa", aggregateByTask(traces));
        out.put("porUsuario", aggregateByUser(traces));
        return out;
    }

    public Map<String, Object> aggregateLastDays(int days) {
        Instant desde = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return aggregate(AiTraceFilterDTO.builder().desde(desde).build());
    }

    public ModelStats statsForModel(List<AiTrace> traces, String modeloDisplay) {
        ModelStats s = new ModelStats(modeloDisplay);
        for (AiTrace t : traces) {
            if (!modeloDisplay.equalsIgnoreCase(nullSafe(t.getModeloEscolhido()))) {
                continue;
            }
            s.accept(t);
        }
        return s;
    }

    List<Map<String, Object>> aggregateByModel(List<AiTrace> traces) {
        Map<String, ModelStats> map = new LinkedHashMap<>();
        for (AiTrace t : traces) {
            String m = nullSafe(t.getModeloEscolhido());
            if (m.isBlank()) {
                m = "Desconhecido";
            }
            map.computeIfAbsent(m, ModelStats::new).accept(t);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModelStats s : map.values()) {
            out.add(s.toMap());
        }
        return out;
    }

    private Map<String, Object> aggregateByTask(List<AiTrace> traces) {
        Map<String, ModelStats> map = new LinkedHashMap<>();
        for (AiTrace t : traces) {
            String key = t.getTaskType() != null ? t.getTaskType().name() : "UNKNOWN";
            map.computeIfAbsent(key, k -> new ModelStats(k)).accept(t);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            out.put(e.getKey(), e.getValue().toMap());
        }
        return out;
    }

    private Map<String, Object> aggregateByUser(List<AiTrace> traces) {
        Map<Long, ModelStats> map = new LinkedHashMap<>();
        for (AiTrace t : traces) {
            Long uid = t.getUserId() != null ? t.getUserId() : 0L;
            map.computeIfAbsent(uid, id -> new ModelStats("user:" + id)).accept(t);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue().toMap());
        }
        return out;
    }

    public List<AiTrace> tracesBetween(Instant desde, Instant ate) {
        return traceStore.find(AiTraceFilterDTO.builder().desde(desde).ate(ate).build(), 10_000, 0);
    }

    public List<AiTrace> tracesForTask(AITaskType task, Instant desde) {
        return traceStore.find(
            AiTraceFilterDTO.builder().taskType(task).desde(desde).build(),
            10_000,
            0
        );
    }

    static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public static final class ModelStats {
        private final String label;
        private long total;
        private long sucesso;
        private long falhas;
        private long fallbacks;
        private long retries;
        private long structuredInvalid;
        private long structuredTotal;
        private long latencySum;
        private long latencyMin = Long.MAX_VALUE;
        private long latencyMax;
        private long tokensIn;
        private long tokensOut;
        private double costTotal;

        ModelStats(String label) {
            this.label = label;
        }

        void accept(AiTrace t) {
            total++;
            if (t.getStatus() == AiTraceStatus.SUCCESS) {
                sucesso++;
            } else {
                falhas++;
            }
            if (t.isFallbackUtilizado()) {
                fallbacks++;
            }
            if (t.getTentativas() > 1) {
                retries++;
            }
            if (t.getStructuredOutputValido() != null) {
                structuredTotal++;
                if (!Boolean.TRUE.equals(t.getStructuredOutputValido())) {
                    structuredInvalid++;
                }
            }
            latencySum += t.getDuracaoMs();
            latencyMin = Math.min(latencyMin, t.getDuracaoMs());
            latencyMax = Math.max(latencyMax, t.getDuracaoMs());
            tokensIn += t.getTokensEntrada();
            tokensOut += t.getTokensSaida();
            costTotal += t.getCustoEstimadoUsd();
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", label);
            m.put("totalChamadas", total);
            m.put("taxaSucesso", pct(sucesso, total));
            m.put("taxaFallback", pct(fallbacks, total));
            m.put("taxaRetry", pct(retries, total));
            m.put("taxaStructuredInvalido", pct(structuredInvalid, structuredTotal));
            m.put("tempoMedioMs", total > 0 ? round1(latencySum / (double) total) : 0);
            m.put("tempoMinMs", total > 0 && latencyMin != Long.MAX_VALUE ? latencyMin : 0);
            m.put("tempoMaxMs", latencyMax);
            m.put("tokensMedios", total > 0 ? round1((tokensIn + tokensOut) / (double) total) : 0);
            m.put("tokensTotais", tokensIn + tokensOut);
            m.put("custoMedioUsd", total > 0 ? round5(costTotal / total) : 0);
            m.put("custoTotalUsd", round5(costTotal));
            return m;
        }

        long getTotal() {
            return total;
        }

        double successRate() {
            return total > 0 ? sucesso / (double) total : 0;
        }

        double fallbackRate() {
            return total > 0 ? fallbacks / (double) total : 0;
        }

        double avgLatencyMs() {
            return total > 0 ? latencySum / (double) total : 0;
        }

        double avgCostUsd() {
            return total > 0 ? costTotal / total : 0;
        }

        double structuredInvalidRate() {
            return structuredTotal > 0 ? structuredInvalid / (double) structuredTotal : 0;
        }
    }

    static double pct(long part, long whole) {
        if (whole <= 0) {
            return 0;
        }
        return round1(part * 100.0 / whole);
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    static double round5(double v) {
        return Math.round(v * 100_000.0) / 100_000.0;
    }
}
