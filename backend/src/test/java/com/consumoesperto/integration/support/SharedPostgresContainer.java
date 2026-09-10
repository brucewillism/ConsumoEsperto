package com.consumoesperto.integration.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Um contentor Postgres por JVM para os testes Testcontainers “vanilla”.
 * Evita N {@code docker create} na suíte (Podman 2 GiB rebentava com {@code localhost:2375}).
 * Cada classe usa uma <em>database</em> isolada — Hibernate {@code create-drop} não apaga o schema Flyway das outras.
 * <p>
 * Não usa {@code withReuse(true)}: isso exige {@code ~/.testcontainers.properties} no utilizador.
 * Não força npipe/Windows no POM (CI Linux).
 */
public final class SharedPostgresContainer {

    private static final String IMAGE = "postgres:16-alpine";
    private static final Object LOCK = new Object();
    private static volatile PostgreSQLContainer<?> container;

    private SharedPostgresContainer() {
    }

    public static boolean dockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String username() {
        return container().getUsername();
    }

    public static String password() {
        return container().getPassword();
    }

    public static String jdbcUrl(String database) {
        String db = sanitize(database);
        PostgreSQLContainer<?> c = container();
        ensureDatabase(c, db);
        return "jdbc:postgresql://" + c.getHost() + ":" + c.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
            + "/" + db;
    }

    public static JdbcTemplate flywayJdbc(String database) {
        String url = jdbcUrl(database);
        Flyway.configure()
            .dataSource(url, username(), password())
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, username(), password()));
    }

    private static PostgreSQLContainer<?> container() {
        PostgreSQLContainer<?> existing = container;
        if (existing != null && existing.isRunning()) {
            return existing;
        }
        synchronized (LOCK) {
            if (container != null && container.isRunning()) {
                return container;
            }
            if (!dockerAvailable()) {
                throw new IllegalStateException("Docker/Podman indisponível para Testcontainers");
            }
            startWithRetry();
            PostgreSQLContainer<?> started = container;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (started != null && started.isRunning()) {
                        started.stop();
                    }
                } catch (Exception ignored) {
                    // JVM a sair
                }
            }, "shared-postgres-stop"));
            return container;
        }
    }

    private static void startWithRetry() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            PostgreSQLContainer<?> candidate = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("postgres")
                .withUsername("consumo")
                .withPassword("test");
            try {
                candidate.start();
                container = candidate;
                return;
            } catch (RuntimeException e) {
                last = e;
                try {
                    candidate.stop();
                } catch (Exception ignored) {
                    // contentor a meio do start
                }
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private static String sanitize(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("nome de database inválido: " + name);
        }
        return name;
    }

    private static void ensureDatabase(PostgreSQLContainer<?> c, String db) {
        try (Connection conn = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        } catch (SQLException e) {
            if ("42P04".equals(e.getSQLState())) {
                return;
            }
            throw new IllegalStateException("Não foi possível criar database " + db, e);
        }
    }
}
