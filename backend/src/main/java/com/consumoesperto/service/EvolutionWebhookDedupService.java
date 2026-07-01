package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotência persistente de webhooks Evolution — {@code INSERT ... ON CONFLICT DO NOTHING}.
 * Fallback in-memory quando a tabela ainda não existir (dev/local).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookDedupService {

    private static final int MAX_KEYS = 15_000;
    private static final long PRUNE_AGE_MS = 48L * 60 * 60 * 1000;

    private final JdbcTemplate jdbcTemplate;

    private final Map<String, Long> memoriaFallback = new ConcurrentHashMap<>();
    private volatile boolean tabelaDisponivel = true;

    public boolean claimDelivery(
        String evolutionInstance,
        String messageKeyId,
        String remoteJid,
        boolean fromMe,
        String bodyPreview
    ) {
        String key = montarChave(evolutionInstance, messageKeyId, remoteJid, fromMe, bodyPreview);
        if (tabelaDisponivel) {
            try {
                int inserted = jdbcTemplate.update(
                    "INSERT INTO evento_webhook_processado (chave_dedup) VALUES (?) ON CONFLICT (chave_dedup) DO NOTHING",
                    key
                );
                if (inserted == 0) {
                    log.info("[WhatsAppFilter] Mensagem ignorada: duplicada (DB) key={} instance={}", messageKeyId, evolutionInstance);
                    return false;
                }
                return true;
            } catch (DataAccessException ex) {
                tabelaDisponivel = false;
                log.warn("[WhatsAppFilter] Tabela evento_webhook_processado indisponível — fallback memória: {}", ex.getMessage());
            }
        }
        return claimMemoria(key, messageKeyId, evolutionInstance);
    }

    private boolean claimMemoria(String key, String messageKeyId, String evolutionInstance) {
        long now = System.currentTimeMillis();
        Long prev = memoriaFallback.putIfAbsent(key, now);
        if (prev != null) {
            log.info("[WhatsAppFilter] Mensagem ignorada: duplicada (mem) messageKeyId={} instance={}", messageKeyId, evolutionInstance);
            return false;
        }
        if (memoriaFallback.size() > MAX_KEYS) {
            memoriaFallback.entrySet().removeIf(e -> e.getValue() != null && e.getValue() < now - PRUNE_AGE_MS);
        }
        return true;
    }

    static String montarChave(
        String evolutionInstance,
        String messageKeyId,
        String remoteJid,
        boolean fromMe,
        String bodyPreview
    ) {
        String inst = evolutionInstance == null ? "" : evolutionInstance.trim();
        if (messageKeyId != null && !messageKeyId.isBlank()) {
            return inst + "|id|" + messageKeyId.trim();
        }
        String rj = remoteJid == null ? "" : remoteJid.trim();
        String norm = bodyPreview == null ? "" : bodyPreview.trim();
        if (norm.length() > 320) {
            norm = norm.substring(0, 320);
        }
        return inst + "|noid|" + rj + "|" + fromMe + "|" + Integer.toHexString(norm.hashCode());
    }
}
