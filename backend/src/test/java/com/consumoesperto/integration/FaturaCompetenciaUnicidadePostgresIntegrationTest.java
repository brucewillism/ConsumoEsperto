package com.consumoesperto.integration;

import com.consumoesperto.integration.support.SharedPostgresContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * {@code ux_faturas_cartao_competencia_nao_quitada} impede a criação concorrente de duas
 * faturas não quitadas para o mesmo cartão + competência.
 */
@EnabledIf("com.consumoesperto.integration.FaturaCompetenciaUnicidadePostgresIntegrationTest#dockerDisponivel")
class FaturaCompetenciaUnicidadePostgresIntegrationTest {

    static boolean dockerDisponivel() {
        return SharedPostgresContainer.dockerAvailable();
    }

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        jdbc = SharedPostgresContainer.flywayJdbc("fatura_competencia");
    }

    private Long cartaoId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM faturas");
        Long usuarioId = jdbc.queryForObject(
            "INSERT INTO usuarios (email, username, nome, password, jarvis_configurado) "
                + "VALUES ('fatura_uni_" + System.nanoTime() + "@test.local', 'fatura_uni_" + System.nanoTime()
                + "', 'Teste', 'x', false) RETURNING id",
            Long.class);
        cartaoId = jdbc.queryForObject(
            "INSERT INTO cartoes_credito (nome, banco, numero_cartao, limite_credito, limite_disponivel, "
                + "dia_vencimento, ativo, usuario_id) "
                + "VALUES ('Cartao Teste', 'Banco', '1234', 5000, 5000, 10, true, " + usuarioId + ") RETURNING id",
            Long.class);
    }

    @Test
    void duasCriacoesConcorrentes_apenasUmaFaturaNaoQuitadaPorCompetencia() throws Exception {
        int threads = 4;
        CountDownLatch pronto = new CountDownLatch(1);
        Callable<Boolean> insere = () -> {
            pronto.await();
            try {
                jdbc.update(
                    "INSERT INTO faturas (numero_fatura, status, paga, valor_fatura, valor_total, "
                        + "data_vencimento, data_fechamento, cartao_credito_id) "
                        + "VALUES ('CONC-" + System.nanoTime() + "', 'PREVISTA', false, 0, 0, "
                        + "'2030-05-15 12:00:00', '2030-05-05 12:00:00', " + cartaoId + ")");
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // DuplicateKeyException é subclasse: conflito de unicidade esperado na corrida
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
        assertEquals(1, sucessos, "Somente uma inserção deve vencer a corrida");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM faturas", Integer.class));
    }

    @Test
    void faturaQuitada_naoBloqueiaNovaFaturaDaMesmaCompetencia() {
        jdbc.update(
            "INSERT INTO faturas (numero_fatura, status, paga, valor_fatura, valor_total, "
                + "data_vencimento, data_fechamento, cartao_credito_id) "
                + "VALUES ('PAGA-" + System.nanoTime() + "', 'PAGA', true, 100, 100, "
                + "'2030-06-15 12:00:00', '2030-06-05 12:00:00', " + cartaoId + ")");
        jdbc.update(
            "INSERT INTO faturas (numero_fatura, status, paga, valor_fatura, valor_total, "
                + "data_vencimento, data_fechamento, cartao_credito_id) "
                + "VALUES ('NOVA-" + System.nanoTime() + "', 'ABERTA', false, 0, 0, "
                + "'2030-06-20 12:00:00', '2030-06-10 12:00:00', " + cartaoId + ")");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM faturas", Integer.class));
    }
}
