package com.consumoesperto.service.ai;

import com.consumoesperto.config.AiRouterProperties;
import com.consumoesperto.service.AiProviderType;
import com.consumoesperto.util.AiProviderOrder;
import com.consumoesperto.service.AiProvidersConfigService.AiProvidersConfig;
import com.consumoesperto.service.ai.trace.AiTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Roteador inteligente: escolhe modelo por tipo de tarefa e aplica fallback apenas na cadeia da categoria.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiRouterService {

    private final AiRouterProperties properties;
    private final AiRouterMetrics metrics;
    private final AiTraceService traceService;

    @FunctionalInterface
    public interface RouterAttempt<T> {
        T execute(AiProviderType provider, AiProvidersConfig cfg) throws Exception;
    }

    public <T> T route(
        AITaskType taskType,
        AiProvidersConfig cfg,
        Predicate<AiProviderType> canUseProvider,
        String inputForTokenEstimate,
        RouterAttempt<T> attempt,
        String failureMessagePrefix
    ) {
        return route(AiRouterRequestContext.of(null), taskType, cfg, canUseProvider, inputForTokenEstimate, attempt, failureMessagePrefix);
    }

    /**
     * Executa a tarefa percorrendo a cadeia de provedores definida para {@code taskType}.
     */
    public <T> T route(
        AiRouterRequestContext context,
        AITaskType taskType,
        AiProvidersConfig cfg,
        Predicate<AiProviderType> canUseProvider,
        String inputForTokenEstimate,
        RouterAttempt<T> attempt,
        String failureMessagePrefix
    ) {
        List<AiProviderType> chain = resolveChain(taskType);
        List<String> errors = new ArrayList<>();
        int attemptIndex = 0;

        String preferencial = chain.isEmpty() ? "-" : AiRouterMetrics.displayName(chain.get(0));
        Long userId = context != null ? context.getUserId() : null;
        Double temperatura = context != null ? context.getTemperature() : null;
        var trace = traceService.begin(userId, taskType, preferencial, temperatura);

        try {
            for (AiProviderType provider : chain) {
                if (!canUseProvider.test(provider)) {
                    continue;
                }
                boolean usedFallback = attemptIndex > 0;
                String modeloDisplay = AiRouterMetrics.displayName(provider);
                long start = System.currentTimeMillis();
                try {
                    T result = attempt.execute(provider, cfg);
                    long latency = System.currentTimeMillis() - start;
                    int inTok = AiRouterMetrics.estimateTokens(inputForTokenEstimate);
                    int outTok = AiRouterMetrics.estimateTokens(result != null ? result.toString() : "");
                    double cost = AiRouterMetrics.estimateCostUsd(provider, inTok, outTok);
                    trace.addAttempt(modeloDisplay, false);
                    metrics.recordSuccess(taskType, provider, latency, inTok, outTok, cost, usedFallback);
                    traceService.finalizeSuccess(trace, modeloDisplay, latency, inTok, outTok, cost, usedFallback);
                    if (usedFallback) {
                        log.info(
                            "[AiRouter] fallback task={} provider={} (tentativa #{}) latencyMs={}",
                            taskType.name(), provider.name(), attemptIndex + 1, latency
                        );
                    } else {
                        log.debug("[AiRouter] task={} provider={} latencyMs={}", taskType.name(), provider.name(), latency);
                    }
                    return result;
                } catch (Exception e) {
                    long latency = System.currentTimeMillis() - start;
                    trace.addAttempt(modeloDisplay, true);
                    metrics.recordFailure(taskType, provider, latency);
                    errors.add(provider.name() + ": " + e.getMessage());
                    log.warn("[AiRouter] falha task={} provider={}: {}", taskType.name(), provider.name(), e.getMessage());
                }
                attemptIndex++;
            }

            String erro;
            if (errors.isEmpty()) {
                erro = failureMessagePrefix + "nenhum provedor elegível na cadeia " + taskType.name()
                    + " (credenciais desabilitadas ou ausentes).";
            } else {
                erro = failureMessagePrefix + String.join(" | ", errors);
            }
            traceService.finalizeFailure(trace, erro);
            throw new RuntimeException(erro);
        } catch (RuntimeException e) {
            if (trace.getStatus() == null) {
                traceService.finalizeFailure(trace, e.getMessage());
            }
            throw e;
        }
    }

    public List<AiProviderType> resolveChain(AITaskType taskType) {
        List<AiProviderType> baseChain = properties.isEnabled()
            ? taskType.getProviderChain()
            : AiProviderOrder.canonicalTypes();
        List<AiProviderType> chain = new ArrayList<>();
        for (AiProviderType provider : baseChain) {
            if (isProviderEnabled(provider)) {
                chain.add(provider);
            }
        }
        if (properties.isOllamaEmergencyFallback()
            && isProviderEnabled(AiProviderType.OLLAMA)
            && !chain.contains(AiProviderType.OLLAMA)) {
            chain.add(AiProviderType.OLLAMA);
        }
        return chain;
    }

    public boolean isProviderEnabled(AiProviderType provider) {
        return switch (provider) {
            case GROQ -> properties.getGroq().isEnabled();
            case OPENAI -> properties.getOpenai().isEnabled();
            case CLAUDE -> properties.getClaude().isEnabled();
            case GEMINI -> properties.getGemini().isEnabled();
            case DEEPSEEK -> properties.getDeepseek().isEnabled();
            case OLLAMA -> properties.getOllama().isEnabled();
        };
    }

    public Map<String, Object> metricsSnapshot() {
        return metrics.snapshot();
    }
}
