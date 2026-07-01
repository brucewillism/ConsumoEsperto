package com.consumoesperto.service;

import com.consumoesperto.exception.AiUnavailableException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit por usuário e por número WhatsApp nas rotas de IA.
 * In-memory — suficiente para VPS única; não sobrevive a restart/cluster (aceitável neste deploy).
 */
@Service
@Slf4j
public class AiRateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${consumoesperto.ai.rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;

    @Value("${consumoesperto.ai.rate-limit.whatsapp-per-number-per-minute:20}")
    private int whatsappPerNumberPerMinute;

    public void checkOrThrow(Long userId, String canal) {
        checkOrThrow(userId, canal, null);
    }

    public void checkOrThrow(Long userId, String canal, String whatsappJid) {
        if (userId != null) {
            consume(userId + "|" + (canal != null ? canal : "default"), requestsPerMinute);
        }
        if (whatsappJid != null && !whatsappJid.isBlank()) {
            consume("phone|" + normalizeJid(whatsappJid), whatsappPerNumberPerMinute);
        }
    }

    private void consume(String key, int rpm) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(rpm));
        if (!bucket.tryConsume(1)) {
            log.warn("[AI-RATE-LIMIT] chave={} excedeu {} req/min", key, rpm);
            throw new AiUnavailableException(
                "Limite de consultas à IA atingido. Aguarde um minuto e tente novamente.");
        }
    }

    private Bucket newBucket(int rpm) {
        int limit = Math.max(5, rpm);
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private static String normalizeJid(String jid) {
        return jid.trim().toLowerCase().replaceAll("\\s+", "");
    }
}
