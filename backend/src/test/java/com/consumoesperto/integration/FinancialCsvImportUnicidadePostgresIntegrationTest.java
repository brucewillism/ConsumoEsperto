package com.consumoesperto.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Índice único de fingerprint impede duas transações idênticas no PostgreSQL real.
 */
@Testcontainers
@EnabledIf("com.consumoesperto.integration.FinancialCsvImportUnicidadePostgresIntegrationTest#dockerDisponivel")
class FinancialCsvImportUnicidadePostgresIntegrationTest {

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

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    private Long usuarioId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM transacoes");
        usuarioId = jdbc.queryForObject(
            "INSERT INTO usuarios (email, username, nome, password, jarvis_configurado) "
                + "VALUES ('csv_fp_" + System.nanoTime() + "@test.local', 'csv_fp_" + System.nanoTime()
                + "', 'Teste', 'x', false) RETURNING id",
            Long.class);
    }

    @Test
    void duasInsercoesConcorrentesComMesmoFingerprint_apenasUmaPersiste() throws Exception {
        String fp = "abc123fingerprintcsv";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> insert = () -> {
            start.await();
            try {
                jdbc.update(
                    "INSERT INTO transacoes (descricao, valor, tipo_transacao, data_transacao, excluido, "
                        + "recorrente, status_conferencia, usuario_id, ingestion_fingerprint) "
                        + "VALUES ('POSTO SHELL', 89.90, 'DESPESA', now(), false, false, 'CONFIRMADA', ?, ?)",
                    usuarioId, fp);
                return 1;
            } catch (Exception e) {
                return 0;
            }
        };
        Future<Integer> a = pool.submit(insert);
        Future<Integer> b = pool.submit(insert);
        start.countDown();
        int ok = a.get() + b.get();
        pool.shutdownNow();
        assertTrue(ok >= 1);
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
            "SELECT COUNT(*) FROM transacoes WHERE usuario_id = ? AND ingestion_fingerprint = ?",
            Integer.class, usuarioId, fp));
    }
}
