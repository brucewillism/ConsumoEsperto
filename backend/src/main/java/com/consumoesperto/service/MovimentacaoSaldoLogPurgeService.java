package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Expurgo por retenção da trilha {@code movimentacao_saldo_log} — única remoção permitida
 * na tabela append-only. Retenção longa por padrão (auditoria financeira).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovimentacaoSaldoLogPurgeService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${consumoesperto.saldo.audit.retention-days:730}")
    private int retentionDays;

    @Scheduled(cron = "0 45 4 * * ?", zone = "America/Sao_Paulo")
    public void expurgarRegistrosAntigos() {
        int dias = Math.max(90, retentionDays);
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM movimentacao_saldo_log WHERE criado_em < NOW() - (? * INTERVAL '1 day')",
                dias
            );
            if (deleted > 0) {
                log.info("[AUDIT-SALDO] Expurgados {} registro(s) com mais de {} dia(s)", deleted, dias);
            }
        } catch (Exception ex) {
            log.warn("[AUDIT-SALDO] Falha no expurgo: {}", ex.getMessage());
        }
    }
}
