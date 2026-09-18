package com.consumoesperto.integration;

import com.consumoesperto.integration.support.SharedPostgresContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIf("com.consumoesperto.integration.DashboardViewFlywayIntegrationTest#dockerDisponivel")
class DashboardViewFlywayIntegrationTest {

    static boolean dockerDisponivel() {
        return SharedPostgresContainer.dockerAvailable();
    }

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        jdbc = SharedPostgresContainer.flywayJdbc("dashboard_view");
    }

    @Test
    void usuariosTemColunaUltimaVisaoDashboard() {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'usuarios' AND column_name = 'ultima_visao_dashboard'",
            Integer.class);
        assertEquals(1, n);
    }

    @Test
    void importacoesFaturaTemColunasCsvDaV9() {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'importacoes_fatura_cartao' AND column_name IN "
                + "('tipo_arquivo', 'conta_bancaria_id', 'arquivo_nome', 'precisa_escolha_recurso')",
            Integer.class);
        assertEquals(4, n);
    }
}
