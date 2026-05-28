package com.consumoesperto.service.ai;

import com.consumoesperto.service.TokenSuppressorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Gateway único: toda chamada de IA deve passar aqui antes do provider (Groq/OpenAI/Claude/…).
 * Encapsula ATS, estratégia AUTO, cache, retries e métricas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiGatewayService {

    private final TokenSuppressorService tokenSuppressorService;
    private final AiStrategySelector strategySelector;
    private final AiGatewayLocalCache localCache;
    private final AiGatewayMetrics metrics;

    @Value("${consumoesperto.ai.gateway.enabled:true}")
    private boolean gatewayEnabled;

    @Value("${consumoesperto.ai.gateway.auto-strategy:true}")
    private boolean autoStrategy;

    @Value("${consumoesperto.ai.gateway.default-strategy:balanced}")
    private String defaultStrategyName;

    @Value("${consumoesperto.ai.gateway.max-retries:2}")
    private int maxRetries;

    public boolean isActive() {
        return gatewayEnabled && tokenSuppressorService.isEnabled();
    }

    /**
     * Otimiza prompts via ATS; em falha devolve texto original (fallback seguro para o provider).
     */
    public GatewayOptimizationResult optimizeBeforeProvider(
        Long userId,
        String systemPrompt,
        String userPrompt,
        AiGatewayPromptContext context,
        String targetModel
    ) {
        String sys = systemPrompt != null ? systemPrompt : "";
        String usr = userPrompt != null ? userPrompt : "";
        AiGatewayPromptContext ctx = context != null
            ? context
            : AiGatewayPromptContext.fromPrompts(sys, usr);

        if (!gatewayEnabled) {
            metrics.recordPassthrough();
            return GatewayOptimizationResult.passthrough(sys, usr, "gateway-disabled");
        }
        if (!tokenSuppressorService.isEnabled()) {
            metrics.recordPassthrough();
            return GatewayOptimizationResult.passthrough(sys, usr, "ats-disabled");
        }

        AiGatewayStrategy strategy = resolveStrategy(ctx);
        String model = targetModel != null && !targetModel.isBlank()
            ? targetModel
            : (ctx.targetModelHint() != null ? ctx.targetModelHint() : "gemini-2.5-flash");

        String cacheKey = AiGatewayLocalCache.cacheKey(
            userId, strategy.atsValue(), sys, usr, model);
        Optional<AiGatewayLocalCache.CachedOptimization> cached = localCache.get(cacheKey);
        if (cached.isPresent()) {
            metrics.recordCacheHit();
            AiGatewayLocalCache.CachedOptimization c = cached.get();
            log.debug("[AiGateway] cache hit strategy={} saved~{}", c.strategy(), c.tokensSaved());
            return new GatewayOptimizationResult(
                c.systemPrompt(),
                c.userPrompt(),
                c.tokensSaved(),
                c.strategy(),
                true,
                false
            );
        }

        metrics.recordAttempt();
        int attempts = Math.max(0, Math.min(maxRetries, 5));
        for (int i = 0; i <= attempts; i++) {
            try {
                Optional<TokenSuppressorService.OptimizedPrompt> opt = tokenSuppressorService.tryOptimize(
                    userId, sys, usr, model, strategy.atsValue());
                if (opt.isPresent()) {
                    TokenSuppressorService.OptimizedPrompt o = opt.get();
                    metrics.recordSuccess(o.tokensSaved());
                    localCache.put(cacheKey, new AiGatewayLocalCache.CachedOptimization(
                        o.systemPrompt(),
                        o.userPrompt(),
                        o.tokensSaved(),
                        strategy.atsValue()
                    ));
                    log.info(
                        "[AiGateway] userId={} strategy={} tokensSaved~{} model={}",
                        userId, strategy.atsValue(), o.tokensSaved(), model
                    );
                    return new GatewayOptimizationResult(
                        o.systemPrompt(),
                        o.userPrompt(),
                        o.tokensSaved(),
                        strategy.atsValue(),
                        true,
                        false
                    );
                }
                break;
            } catch (Exception e) {
                metrics.recordFailure();
                log.warn("[AiGateway] tentativa {}/{} falhou: {}", i + 1, attempts + 1, e.getMessage());
                if (i < attempts) {
                    pauseBackoff(i);
                }
            }
        }

        metrics.recordFailure();
        log.warn("[AiGateway] ATS sem otimização — a usar prompt original (strategy={})", strategy.atsValue());
        return GatewayOptimizationResult.passthrough(sys, usr, "ats-fallback");
    }

    public AiGatewayStrategy resolveStrategy(AiGatewayPromptContext ctx) {
        if (autoStrategy) {
            return strategySelector.selectStrategy(ctx);
        }
        return AiGatewayStrategy.fromAtsValue(defaultStrategyName);
    }

    private static void pauseBackoff(int attempt) {
        try {
            Thread.sleep(200L * (attempt + 1L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public record GatewayOptimizationResult(
        String systemPrompt,
        String userPrompt,
        int tokensSaved,
        String strategyUsed,
        boolean optimizedByAts,
        boolean fromCache
    ) {
        public static GatewayOptimizationResult passthrough(String sys, String usr, String reason) {
            return new GatewayOptimizationResult(sys, usr, 0, reason, false, false);
        }
    }
}
