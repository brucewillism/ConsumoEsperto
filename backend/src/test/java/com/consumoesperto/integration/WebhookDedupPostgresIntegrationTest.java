package com.consumoesperto.integration;

import com.consumoesperto.service.EvolutionWebhookDedupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integração Postgres: dedup webhook (INSERT ON CONFLICT) e expurgo. */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("com.consumoesperto.integration.WebhookDedupPostgresIntegrationTest#dockerDisponivel")
class WebhookDedupPostgresIntegrationTest {

    static boolean dockerDisponivel() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("consumo_test")
        .withUsername("consumo")
        .withPassword("test");

    private EvolutionWebhookDedupService dedupService;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource ds = new DriverManagerDataSource(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS evento_webhook_processado ("
                + "id BIGSERIAL PRIMARY KEY,"
                + "chave_dedup VARCHAR(512) NOT NULL UNIQUE,"
                + "processado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.execute("TRUNCATE evento_webhook_processado");
        dedupService = new EvolutionWebhookDedupService(jdbcTemplate);
    }

    @Test
    void claimDelivery_apenasPrimeiroClaimVence() throws Exception {
        Callable<Boolean> task = () -> dedupService.claimDelivery("inst", "MSG-1", "+5511@s.whatsapp.net", false, "oi");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(task));
        }
        int wins = 0;
        for (Future<Boolean> f : futures) {
            if (Boolean.TRUE.equals(f.get())) {
                wins++;
            }
        }
        pool.shutdown();
        assertEquals(1, wins);
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento_webhook_processado", Integer.class));
    }

    @Test
    void reenvioMesmoEvento_naoDisparaSegundoClaim() {
        assertTrue(dedupService.claimDelivery("inst", "MSG-2", "+5512@s.whatsapp.net", false, "teste"));
        assertFalse(dedupService.claimDelivery("inst", "MSG-2", "+5512@s.whatsapp.net", false, "teste"));
    }
}
