package com.consumoesperto.integration;

import com.consumoesperto.integration.support.SharedPostgresContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Índice único parcial (usuario_id, id_externo) no PostgreSQL real — dedup de notificação repetida.
 */
@EnabledIf("com.consumoesperto.integration.IngestNotificacaoDedupPostgresIntegrationTest#dockerDisponivel")
class IngestNotificacaoDedupPostgresIntegrationTest {

    static boolean dockerDisponivel() {
        return SharedPostgresContainer.dockerAvailable();
    }

    private static JdbcTemplate jdbc;
    private Long usuarioId;

    @BeforeAll
    static void migrate() {
        jdbc = SharedPostgresContainer.flywayJdbc("ingest_notif");
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM notificacao_bancaria_recebida");
        usuarioId = jdbc.queryForObject(
            "INSERT INTO usuarios (email, username, nome, password, jarvis_configurado) "
                + "VALUES ('ing_pg_" + System.nanoTime() + "@test.local', 'ing_pg_" + System.nanoTime()
                + "', 'Teste', 'x', false) RETURNING id",
            Long.class);
    }

    @Test
    void mesmoIdExterno_segundaInsercaoFalha() {
        insertNotif("ext-1", "aaa");
        assertThrows(Exception.class, () -> insertNotif("ext-1", "bbb"));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
            "SELECT COUNT(*) FROM notificacao_bancaria_recebida WHERE usuario_id = ? AND id_externo = ?",
            Integer.class, usuarioId, "ext-1"));
    }

    @Test
    void idExternoNulo_permiteVariasLinhas() {
        insertNotif(null, "h1");
        insertNotif(null, "h2");
        assertEquals(Integer.valueOf(2), jdbc.queryForObject(
            "SELECT COUNT(*) FROM notificacao_bancaria_recebida WHERE usuario_id = ?",
            Integer.class, usuarioId));
    }

    private void insertNotif(String idExterno, String hash) {
        jdbc.update(
            "INSERT INTO notificacao_bancaria_recebida "
                + "(usuario_id, origem, app, texto, recebido_em, id_externo, hash_dedup, status, aviso_pendente, criado_em) "
                + "VALUES (?, 'ANDROID_MACRODROID', 'nubank', 'x', now(), ?, ?, 'RECEBIDA', false, now())",
            usuarioId, idExterno, hash);
    }
}
