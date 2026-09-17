package com.consumoesperto.autonomy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backoff exponencial por tabela + jitter. Sem retry infinito.
 */
public final class OutboxBackoff {

    private OutboxBackoff() {}

    public static Duration delayForAttempt(int attempt, List<Integer> delaysSeconds, double jitterRatio) {
        List<Integer> delays = (delaysSeconds == null || delaysSeconds.isEmpty())
            ? List.of(30, 120, 600, 1800)
            : delaysSeconds;
        int idx = Math.min(Math.max(attempt, 1), delays.size()) - 1;
        long base = Math.max(1, delays.get(idx));
        double ratio = Math.max(0, Math.min(jitterRatio, 1.0));
        long jitter = Math.round(base * ratio * ThreadLocalRandom.current().nextDouble());
        return Duration.ofSeconds(base + jitter);
    }
}
