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
     * @param remoteJid     JID efetivo (só usado se {@code messageKeyId} vier vazio)
     * @param fromMe        como em {@code key.fromMe} — distingue eco legítimo
     * @param bodyPreview   texto (ou vazio) para compor chave sintética quando não há {@code key.id}
     * @return {@code true} se esta entrega deve ser processada; {@code false} se já foi vista.
     */
    public boolean claimDelivery(String evolutionInstance, String messageKeyId, String remoteJid, boolean fromMe, String bodyPreview) {
        String inst = evolutionInstance == null ? "" : evolutionInstance.trim();
        String key;
        if (messageKeyId != null && !messageKeyId.isBlank()) {
            key = inst + "|id|" + messageKeyId.trim();
        } else {
            String rj = remoteJid == null ? "" : remoteJid.trim();
            String norm = bodyPreview == null ? "" : bodyPreview.trim();
            if (norm.length() > 320) {
                norm = norm.substring(0, 320);
            }
            int h = norm.hashCode();
            key = inst + "|noid|" + rj + "|" + fromMe + "|" + Integer.toHexString(h);
        }
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
