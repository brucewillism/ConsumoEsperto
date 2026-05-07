package com.consumoesperto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evita processar duas vezes o mesmo {@code message.key.id} (reenvio do webhook Evolution após falha).
 */
@Service
@Slf4j
public class EvolutionWebhookDedupService {

    private static final int MAX_KEYS = 15_000;
    private static final long PRUNE_AGE_MS = 48L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, Long> seenAt = new ConcurrentHashMap<>();

    /**
     * @return {@code true} se esta entrega deve ser processada; {@code false} se já foi vista.
     */
    public boolean claimDelivery(String evolutionInstance, String messageKeyId) {
        if (messageKeyId == null || messageKeyId.isBlank()) {
            return true;
        }
        String inst = evolutionInstance == null ? "" : evolutionInstance.trim();
        String key = inst + "|" + messageKeyId.trim();
        long now = System.currentTimeMillis();
        Long prev = seenAt.putIfAbsent(key, now);
        if (prev != null) {
            log.info("[WhatsAppFilter] Mensagem ignorada: duplicada messageKeyId={} instance={}", messageKeyId, inst);
            return false;
        }
        if (seenAt.size() > MAX_KEYS) {
            pruneOlderThan(now - PRUNE_AGE_MS);
        }
        return true;
    }

    private void pruneOlderThan(long cutoffMs) {
        seenAt.entrySet().removeIf(e -> e.getValue() != null && e.getValue() < cutoffMs);
    }
}
