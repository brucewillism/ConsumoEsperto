package com.consumoesperto.integration;

import com.consumoesperto.integration.support.SharedPostgresContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dois nós: só um drena o lote. Lock expirado permite ao outro assumir.
 */
@EnabledIf("com.consumoesperto.integration.AutonomyJobLockPostgresIntegrationTest#dockerDisponivel")
class AutonomyJobLockPostgresIntegrationTest {

    static boolean dockerDisponivel() {
        return SharedPostgresContainer.dockerAvailable();
    }

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        jdbc = SharedPostgresContainer.flywayJdbc("autonomy_lock");
    }

    @Test
    void lockVigenteSegundoNoNaoAssume() {
        String job = "drain-" + System.nanoTime();
        int first = jdbc.update(
            "INSERT INTO autonomy_job_lock (job_name, locked_until, locked_by) VALUES (?, now() + interval '5 minutes', 'node-a')",
            job);
        assertEquals(1, first);
        int steal = jdbc.update(
            "UPDATE autonomy_job_lock SET locked_until = now() + interval '5 minutes', locked_by = 'node-b' "
                + "WHERE job_name = ? AND (locked_until IS NULL OR locked_until <= now())",
            job);
        assertEquals(0, steal);
        String holder = jdbc.queryForObject(
            "SELECT locked_by FROM autonomy_job_lock WHERE job_name = ?", String.class, job);
        assertEquals("node-a", holder);
    }

    @Test
    void lockExpiradoSomenteUmNoAssume() throws Exception {
        String job = "drain-exp-" + System.nanoTime();
        jdbc.update(
            "INSERT INTO autonomy_job_lock (job_name, locked_until, locked_by) VALUES (?, now() - interval '1 minute', 'dead-node')",
            job);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        Callable<Integer> steal = () -> {
            start.await();
            int n = jdbc.update(
                "UPDATE autonomy_job_lock SET locked_until = now() + interval '2 minutes', locked_by = ? "
                    + "WHERE job_name = ? AND (locked_until IS NULL OR locked_until <= now())",
                "node-" + Thread.currentThread().getId(), job);
            if (n == 1) {
                wins.incrementAndGet();
            }
            return n;
        };
        Future<Integer> a = pool.submit(steal);
        Future<Integer> b = pool.submit(steal);
        start.countDown();
        int total = a.get() + b.get();
        pool.shutdownNow();
        assertEquals(1, total);
        assertEquals(1, wins.get());
        Integer holders = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT locked_by) FROM autonomy_job_lock WHERE job_name = ?", Integer.class, job);
        assertEquals(Integer.valueOf(1), holders);
        assertTrue(jdbc.queryForObject(
            "SELECT locked_by FROM autonomy_job_lock WHERE job_name = ?", String.class, job).startsWith("node-"));
    }
}
