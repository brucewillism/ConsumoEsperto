package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Expurgo idempotente da tabela {@code evento_webhook_processado} — evita crescimento ilimitado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookDedupPurgeService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${consumoesperto.webhook.dedup.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 15 4 * * ?", zone = "America/Sao_Paulo")
    public void expurgarRegistrosAntigos() {
        int dias = Math.max(7, retentionDays);
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM evento_webhook_processado WHERE processado_em < NOW() - (? * INTERVAL '1 day')",
                dias
            );
            if (deleted > 0) {
                log.info("[WEBHOOK-DEDUP] Expurgados {} registro(s) com mais de {} dia(s)", deleted, dias);
            }
        } catch (Exception ex) {
            log.warn("[WEBHOOK-DEDUP] Falha no expurgo: {}", ex.getMessage());
        }
    }
}
