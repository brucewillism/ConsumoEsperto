package com.consumoesperto.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Gera {@code src/main/resources/db/migration/V1__baseline_inicial.sql} a partir do schema
 * materializado por Hibernate + SchemaAutoPatch em banco temporario local.
 * Executar explicitamente: {@code mvn test -Dtest=FlywayBaselineLocalGeneratorTest}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("baseline-gen")
@EnabledIf("com.consumoesperto.integration.FlywayBaselineLocalGeneratorTest#baselineGenHabilitado")
class FlywayBaselineLocalGeneratorTest {

    private static final String BASELINE_DB = "consumoesperto_baseline_gen";

    static boolean baselineGenHabilitado() {
        return "true".equalsIgnoreCase(System.getenv("GENERATE_FLYWAY_BASELINE"));
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String user = firstNonBlank(System.getenv("DATABASE_USERNAME"), System.getenv("POSTGRES_USER"));
        String pass = firstNonBlank(System.getenv("DATABASE_PASSWORD"), System.getenv("POSTGRES_PASSWORD"));
        String jwt = System.getenv("JWT_SECRET");
        if (user == null || pass == null || jwt == null) {
            throw new IllegalStateException("DATABASE/POSTGRES credentials e JWT_SECRET necessarios para baseline");
        }
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + BASELINE_DB);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> pass);
        registry.add("jwt.secret", () -> jwt);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void materializarSchemaParaBaseline() throws Exception {
        Integer usuarios = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'usuarios'",
            Integer.class
        );
        if (usuarios == null || usuarios == 0) {
            throw new IllegalStateException(
                "Tabela usuarios ausente. Crie o banco " + BASELINE_DB + " vazio antes de executar este teste."
            );
        }
        List<Map<String, Object>> tabelas = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name"
        );
        Path out = Path.of("target", "baseline-tabelas.txt");
        Files.writeString(out, "Tabelas public: " + tabelas.size() + System.lineSeparator());
        for (Map<String, Object> row : tabelas) {
            Files.writeString(out, "- " + row.get("table_name") + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
