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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valida no PostgreSQL real (com as migrations Flyway do projeto) que o índice único
 * {@code ux_agendamento_execucoes_competencia} garante um único registro de execução
 * por agendamento + competência — proteção contra dois nós, retry, duplo clique,
 * restart e execução manual simultânea ao scheduler.
 */
@Testcontainers
@EnabledIf("com.consumoesperto.integration.AgendamentoIdempotenciaPostgresIntegrationTest#dockerDisponivel")
class AgendamentoIdempotenciaPostgresIntegrationTest {

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

    private Long agendamentoId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM agendamento_execucoes");
        Long usuarioId = jdbc.queryForObject(
            "INSERT INTO usuarios (email, username, nome, password, jarvis_configurado) "
                + "VALUES ('ag_idem_" + System.nanoTime() + "@test.local', 'ag_idem_" + System.nanoTime()
                + "', 'Teste', 'x', false) RETURNING id",
            Long.class);
        Long contaId = jdbc.queryForObject(
            "INSERT INTO contas_bancarias (ativa, data_atualizacao, data_criacao, limite_cheque_especial, "
                + "nome, padrao, saldo_atual, saldo_inicial, tipo, usuario_id) "
                + "VALUES (true, now(), now(), 0, 'Conta Teste', true, 5000, 5000, 'CORRENTE', " + usuarioId
                + ") RETURNING id",
            Long.class);
        agendamentoId = jdbc.queryForObject(
            "INSERT INTO agendamentos_pagamentos (beneficiario, data_criacao, data_vencimento, "
                + "falhas_consecutivas, status, valor, conta_debito_id, usuario_id) "
                + "VALUES ('Enel', now(), '2030-07-10', 0, 'AGENDADO', 250.00, " + contaId + ", " + usuarioId
                + ") RETURNING id",
            Long.class);
    }

    @Test
    void execucoesConcorrentes_apenasUmaRegistraACompetencia() throws Exception {
        int threads = 4;
        CountDownLatch pronto = new CountDownLatch(1);
        Callable<Boolean> insere = () -> {
            pronto.await();
            try {
                jdbc.update(
                    "INSERT INTO agendamento_execucoes (agendamento_id, data_execucao, tipo_execucao) "
                        + "VALUES (" + agendamentoId + ", '2030-07-10', 'AUTOMATICA')");
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Conflito de unicidade esperado na corrida = execução já processada
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(insere));
        }
        pronto.countDown();
        int sucessos = 0;
        for (Future<Boolean> f : futures) {
            if (Boolean.TRUE.equals(f.get())) {
                sucessos++;
            }
        }
        pool.shutdown();
        assertEquals(1, sucessos, "Somente uma execução deve vencer a corrida");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM agendamento_execucoes", Integer.class));
    }

    @Test
    void manualSimultaneaAoScheduler_mesmaCompetencia_naoDuplicaDebito() throws Exception {
        CountDownLatch pronto = new CountDownLatch(1);
        Callable<Boolean> manual = inserir("MANUAL", pronto);
        Callable<Boolean> automatica = inserir("AUTOMATICA", pronto);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> f1 = pool.submit(manual);
        Future<Boolean> f2 = pool.submit(automatica);
        pronto.countDown();
        int sucessos = (Boolean.TRUE.equals(f1.get()) ? 1 : 0) + (Boolean.TRUE.equals(f2.get()) ? 1 : 0);
        pool.shutdown();

        assertEquals(1, sucessos, "Manual e scheduler na mesma competência: apenas um débito");
    }

    @Test
    void competenciasDiferentes_naoConflitam() {
        jdbc.update("INSERT INTO agendamento_execucoes (agendamento_id, data_execucao, tipo_execucao) "
            + "VALUES (" + agendamentoId + ", '2030-07-10', 'AUTOMATICA')");
        jdbc.update("INSERT INTO agendamento_execucoes (agendamento_id, data_execucao, tipo_execucao) "
            + "VALUES (" + agendamentoId + ", '2030-08-10', 'AUTOMATICA')");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM agendamento_execucoes", Integer.class));
    }

    @Test
    void retryAposDesfazer_ehPermitido() {
        // Simula débito não concretizado: registro criado e desfeito libera a competência
        jdbc.update("INSERT INTO agendamento_execucoes (agendamento_id, data_execucao, tipo_execucao) "
            + "VALUES (" + agendamentoId + ", '2030-07-10', 'AUTOMATICA')");
        jdbc.update("DELETE FROM agendamento_execucoes WHERE agendamento_id = " + agendamentoId
            + " AND data_execucao = '2030-07-10'");
        jdbc.update("INSERT INTO agendamento_execucoes (agendamento_id, data_execucao, tipo_execucao) "
            + "VALUES (" + agendamentoId + ", '2030-07-10', 'MANUAL')");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM agendamento_execucoes", Integer.class));
    }

    private Callable<Boolean> inserir(String tipo, CountDownLatch pronto) {
        return () -> {
            pronto.await();
            try {
                jdbc.update(
                    "INSERT INTO agendamento_execucoes (agendamento_id, data_execucao, tipo_execucao) "
                        + "VALUES (" + agendamentoId + ", '2030-07-10', '" + tipo + "')");
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                return false;
            }
        };
    }
}
