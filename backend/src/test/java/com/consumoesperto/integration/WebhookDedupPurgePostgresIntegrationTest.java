package com.consumoesperto.integration;

import com.consumoesperto.service.EvolutionWebhookDedupPurgeService;
import com.consumoesperto.integration.support.SharedPostgresContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIf("com.consumoesperto.integration.WebhookDedupPurgePostgresIntegrationTest#dockerDisponivel")
class WebhookDedupPurgePostgresIntegrationTest {

    static boolean dockerDisponivel() {
        return SharedPostgresContainer.dockerAvailable();
    }

    private JdbcTemplate jdbcTemplate;
    private EvolutionWebhookDedupPurgeService purgeService;

    @BeforeEach
    void setUp() {
        DataSource ds = new DriverManagerDataSource(
            SharedPostgresContainer.jdbcUrl("webhook_purge"),
            SharedPostgresContainer.username(),
            SharedPostgresContainer.password()
        );
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS evento_webhook_processado ("
                + "id BIGSERIAL PRIMARY KEY,"
                + "chave_dedup VARCHAR(512) NOT NULL UNIQUE,"
                + "processado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.execute("TRUNCATE evento_webhook_processado");
        purgeService = new EvolutionWebhookDedupPurgeService(jdbcTemplate);
        ReflectionTestUtils.setField(purgeService, "retentionDays", 7);
    }

    @Test
    void expurgar_removeRegistrosAntigos() {
        jdbcTemplate.update(
            "INSERT INTO evento_webhook_processado (chave_dedup, processado_em) VALUES (?, NOW() - INTERVAL '10 days')",
            "antigo|id|1"
        );
        jdbcTemplate.update(
            "INSERT INTO evento_webhook_processado (chave_dedup, processado_em) VALUES (?, NOW())",
            "recente|id|2"
        );
        purgeService.expurgarRegistrosAntigos();
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento_webhook_processado", Integer.class));
    }
}
