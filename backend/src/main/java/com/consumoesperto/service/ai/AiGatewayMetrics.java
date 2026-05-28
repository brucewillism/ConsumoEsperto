package com.consumoesperto.service.ai;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Contadores simples em memória (observabilidade leve do gateway). */
@Component
@Getter
public class AiGatewayMetrics {

    private final AtomicLong optimizeAttempts = new AtomicLong();
    private final AtomicLong optimizeSuccess = new AtomicLong();
    private final AtomicLong optimizeFailures = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong tokensSavedTotal = new AtomicLong();
    private final AtomicLong passthroughNoAts = new AtomicLong();

    public void recordAttempt() {
        optimizeAttempts.incrementAndGet();
    }

    public void recordSuccess(int tokensSaved) {
        optimizeSuccess.incrementAndGet();
        if (tokensSaved > 0) {
            tokensSavedTotal.addAndGet(tokensSaved);
        }
    }

    public void recordFailure() {
        optimizeFailures.incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordPassthrough() {
        passthroughNoAts.incrementAndGet();
    }
}
