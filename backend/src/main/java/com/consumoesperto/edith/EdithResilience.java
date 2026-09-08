package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Circuit breaker + retry do cliente E.D.I.T.H. — não registra health indicator (não derruba o core).
 */
@Component
public class EdithResilience {

    public static final String INSTANCE = "edith";

    private final EdithProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public EdithResilience(
        EdithProperties properties,
        ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistry,
        ObjectProvider<RetryRegistry> retryRegistry
    ) {
        this.properties = properties;
        CircuitBreakerRegistry cbReg = circuitBreakerRegistry.getIfAvailable();
        this.circuitBreaker = properties.isCircuitBreakerEnabled() && cbReg != null
            ? cbReg.circuitBreaker(INSTANCE)
            : null;
        this.retry = buildRetry(retryRegistry.getIfAvailable());
    }

    private Retry buildRetry(RetryRegistry registry) {
        if (!properties.isRetryEnabled() || properties.getMaxRetries() <= 0) {
            return null;
        }
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(attempts)
            .waitDuration(Duration.ofMillis(200))
            .retryExceptions(ResourceAccessException.class)
            .build();
        if (registry != null) {
            return registry.retry(INSTANCE, config);
        }
        return Retry.of(INSTANCE, config);
    }

    public boolean isCircuitOpen() {
        return circuitBreaker != null && circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    public String circuitState() {
        if (!properties.isCircuitBreakerEnabled() || circuitBreaker == null) {
            return "DISABLED";
        }
        return circuitBreaker.getState().name();
    }

    public <T> T execute(Supplier<T> supplier) {
        Supplier<T> decorated = supplier;
        if (retry != null) {
            decorated = Retry.decorateSupplier(retry, decorated);
        }
        if (circuitBreaker != null) {
            decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        }
        return decorated.get();
    }

    public Optional<CircuitBreaker> circuitBreaker() {
        return Optional.ofNullable(circuitBreaker);
    }
}
