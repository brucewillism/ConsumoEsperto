package com.consumoesperto.service.ai;

import com.consumoesperto.service.AiProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class AiRouterMetrics {

    private final ConcurrentHashMap<String, ProviderBucket> byProvider = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskBucket> byTask = new ConcurrentHashMap<>();

    public void recordSuccess(
        AITaskType taskType,
        AiProviderType provider,
        long latencyMs,
        int inputTokensEstimate,
        int outputTokensEstimate,
        double costEstimateUsd,
        boolean usedFallback
    ) {
        providerBucket(provider).recordSuccess(latencyMs, inputTokensEstimate, outputTokensEstimate, costEstimateUsd, usedFallback);
        taskBucket(taskType).recordSuccess(provider, latencyMs, usedFallback);
        log.debug(
            "[AiRouter] ok task={} provider={} latencyMs={} fallback={}",
            taskType.name(), provider.name(), latencyMs, usedFallback
        );
    }

    public void recordFailure(AITaskType taskType, AiProviderType provider, long latencyMs) {
        providerBucket(provider).recordFailure(latencyMs);
        taskBucket(taskType).recordFailure(provider, latencyMs);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> modelos = new ArrayList<>();
        for (AiProviderType p : AiProviderType.values()) {
            ProviderBucket b = byProvider.get(displayName(p));
            if (b == null || b.calls.get() == 0) {
                continue;
            }
            modelos.add(b.toMap(displayName(p)));
        }
        out.put("modelos", modelos);
        out.put("totalChamadas", modelos.stream().mapToLong(m -> ((Number) m.get("chamadas")).longValue()).sum());
        out.put("porTarefa", snapshotByTask());
        return out;
    }

    private Map<String, Object> snapshotByTask() {
        Map<String, Object> tasks = new LinkedHashMap<>();
        for (Map.Entry<String, TaskBucket> e : byTask.entrySet()) {
            tasks.put(e.getKey(), e.getValue().toMap());
        }
        return tasks;
    }

    public static String displayName(AiProviderType provider) {
        return switch (provider) {
            case OPENAI -> "GPT";
            case GROQ -> "Groq";
            case CLAUDE -> "Claude";
            case GEMINI -> "Gemini";
            case DEEPSEEK -> "DeepSeek";
            case OLLAMA -> "Ollama";
        };
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    static double estimateCostUsd(AiProviderType provider, int inputTokens, int outputTokens) {
        double inRate;
        double outRate;
        switch (provider) {
            case GROQ -> {
                inRate = 0.05 / 1_000_000;
                outRate = 0.08 / 1_000_000;
            }
            case OPENAI -> {
                inRate = 2.50 / 1_000_000;
                outRate = 10.0 / 1_000_000;
            }
            case CLAUDE -> {
                inRate = 0.80 / 1_000_000;
                outRate = 4.0 / 1_000_000;
            }
            case GEMINI -> {
                inRate = 0.10 / 1_000_000;
                outRate = 0.40 / 1_000_000;
            }
            case DEEPSEEK -> {
                inRate = 0.14 / 1_000_000;
                outRate = 0.28 / 1_000_000;
            }
            case OLLAMA -> {
                return 0.0;
            }
            default -> {
                inRate = 1.0 / 1_000_000;
                outRate = 3.0 / 1_000_000;
            }
        }
        return inputTokens * inRate + outputTokens * outRate;
    }

    private ProviderBucket providerBucket(AiProviderType provider) {
        return byProvider.computeIfAbsent(displayName(provider), k -> new ProviderBucket());
    }

    private TaskBucket taskBucket(AITaskType taskType) {
        return byTask.computeIfAbsent(taskType.name(), k -> new TaskBucket());
    }

    private static final class ProviderBucket {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong fallbacks = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
        private final AtomicLong inputTokens = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();
        private final AtomicLong costMicroUsd = new AtomicLong();

        void recordSuccess(
            long latencyMs,
            int inputTok,
            int outputTok,
            double costUsd,
            boolean usedFallback
        ) {
            calls.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            inputTokens.addAndGet(inputTok);
            outputTokens.addAndGet(outputTok);
            costMicroUsd.addAndGet(Math.round(costUsd * 1_000_000));
            if (usedFallback) {
                fallbacks.incrementAndGet();
            }
        }

        void recordFailure(long latencyMs) {
            failures.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
        }

        Map<String, Object> toMap(String label) {
            long n = calls.get();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modelo", label);
            m.put("chamadas", n);
            m.put("falhas", failures.get());
            m.put("fallbacks", fallbacks.get());
            m.put("tempoMedioMs", n > 0 ? round1(totalLatencyMs.get() / (double) n) : 0.0);
            m.put("tokensEntrada", inputTokens.get());
            m.put("tokensSaida", outputTokens.get());
            m.put("custoEstimadoUsd", round4(costMicroUsd.get() / 1_000_000.0));
            return m;
        }
    }

    private static final class TaskBucket {
        private final AtomicLong calls = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong fallbacks = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
        private volatile String ultimoModelo = "";

        void recordSuccess(AiProviderType provider, long latencyMs, boolean usedFallback) {
            calls.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            ultimoModelo = provider.name();
            if (usedFallback) {
                fallbacks.incrementAndGet();
            }
        }

        void recordFailure(AiProviderType provider, long latencyMs) {
            failures.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            ultimoModelo = provider.name();
        }

        Map<String, Object> toMap() {
            long n = calls.get() + failures.get();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chamadas", calls.get());
            m.put("falhas", failures.get());
            m.put("fallbacks", fallbacks.get());
            m.put("tempoMedioMs", n > 0 ? round1(totalLatencyMs.get() / (double) n) : 0.0);
            m.put("ultimoModelo", ultimoModelo);
            return m;
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
