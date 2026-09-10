package com.consumoesperto.ingest.notificacao.security;

import com.consumoesperto.config.IngestNotificacaoProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class IngestNotificacaoRateLimiter {

    private final IngestNotificacaoProperties properties;
    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    public IngestNotificacaoRateLimiter(IngestNotificacaoProperties properties) {
        this.properties = properties;
    }

    public void checkOrThrow(Long tokenId) {
        if (tokenId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(tokenId, id -> new Window(now));
        synchronized (window) {
            if (now - window.startMs > 60_000L) {
                window.startMs = now;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > properties.getRateLimitPerMinute()) {
                throw new IngestNotificacaoException("Limite de requisições do token de ingestão excedido");
            }
        }
    }

    private static final class Window {
        private long startMs;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
