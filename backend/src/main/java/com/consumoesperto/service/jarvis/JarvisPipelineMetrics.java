package com.consumoesperto.service.jarvis;

import com.consumoesperto.config.JarvisPerformanceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas de latência do pipeline J.A.R.V.I.S. (p50/p95 por etapa, taxa FAST_PATH vs LLM).
 */
@Service
@RequiredArgsConstructor
public class JarvisPipelineMetrics {

    private final JarvisPerformanceProperties props;

    private final Map<String, Deque<Long>> stageSamples = new ConcurrentHashMap<>();
    private final AtomicLong fastPathCount = new AtomicLong();
    private final AtomicLong llmPathCount = new AtomicLong();
    private final AtomicLong totalRequests = new AtomicLong();

    public JarvisPipelineTrace startTrace(Long userId, String canal) {
        totalRequests.incrementAndGet();
        return new JarvisPipelineTrace(userId, canal, System.nanoTime());
    }

    public void recordStage(JarvisPipelineTrace trace, String stage, long durationMs) {
        if (trace != null) {
            trace.record(stage, durationMs);
        }
        stageSamples.computeIfAbsent(stage, k -> new ArrayDeque<>()).addLast(durationMs);
        trim(stage);
    }

    public void recordRoute(String route) {
        if ("FAST_PATH".equalsIgnoreCase(route)) {
            fastPathCount.incrementAndGet();
        } else if ("LLM".equalsIgnoreCase(route)) {
            llmPathCount.incrementAndGet();
        }
    }

    public void finishTrace(JarvisPipelineTrace trace) {
        if (trace == null) {
            return;
        }
        long totalMs = trace.totalMs();
        recordStage(trace, "total", totalMs);
        org.slf4j.LoggerFactory.getLogger(JarvisPipelineMetrics.class).info(
            "[JARVIS-PERF] canal={} userId={} route={} totalMs={} stages={}",
            trace.canal(), trace.userId(), trace.route(), totalMs, trace.stages());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRequests", totalRequests.get());
        out.put("fastPathCount", fastPathCount.get());
        out.put("llmPathCount", llmPathCount.get());
        Map<String, Map<String, Long>> stages = new LinkedHashMap<>();
        for (Map.Entry<String, Deque<Long>> e : stageSamples.entrySet()) {
            List<Long> sorted = new ArrayList<>(e.getValue());
            Collections.sort(sorted);
            Map<String, Long> stats = new LinkedHashMap<>();
            stats.put("count", (long) sorted.size());
            stats.put("p50Ms", percentile(sorted, 50));
            stats.put("p95Ms", percentile(sorted, 95));
            stages.put(e.getKey(), stats);
        }
        out.put("stages", stages);
        return out;
    }

    private void trim(String stage) {
        Deque<Long> q = stageSamples.get(stage);
        if (q == null) {
            return;
        }
        while (q.size() > props.getMetricsSampleSize()) {
            q.pollFirst();
        }
    }

    private static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(pct / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }
}
