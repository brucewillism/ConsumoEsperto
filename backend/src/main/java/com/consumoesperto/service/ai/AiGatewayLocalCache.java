package com.consumoesperto.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache local de prompts já otimizados pelo ATS (evita chamadas repetidas no mesmo conteúdo).
 */
@Component
public class AiGatewayLocalCache {

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final long ttlMs;

    public AiGatewayLocalCache(
        @Value("${consumoesperto.ai.gateway.cache-ttl-ms:300000}") long ttlMs
    ) {
        this.ttlMs = Math.max(5_000L, ttlMs);
    }

    public Optional<CachedOptimization> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    public void put(String key, CachedOptimization value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        store.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlMs));
    }

    public static String cacheKey(
        Long userId,
        String strategy,
        String systemPrompt,
        String userPrompt,
        String targetModel
    ) {
        String raw = (userId != null ? userId : 0)
            + "|" + nullToEmpty(strategy)
            + "|" + nullToEmpty(targetModel)
            + "|" + nullToEmpty(systemPrompt)
            + "|" + nullToEmpty(userPrompt);
        return sha256Short(raw);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String sha256Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public record CachedOptimization(
        String systemPrompt,
        String userPrompt,
        int tokensSaved,
        String strategy
    ) {}

    private record CacheEntry(CachedOptimization value, long expiresAtMs) {}
}
