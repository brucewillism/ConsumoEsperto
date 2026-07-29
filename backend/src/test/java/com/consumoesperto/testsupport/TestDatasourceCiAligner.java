package com.consumoesperto.testsupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Quando {@code DATABASE_URL} aponta para Postgres (CI), alinha driver/dialect/Flyway
 * antes da resolução de {@code application-test.properties} — evita H2 driver + URL Postgres.
 */
public class TestDatasourceCiAligner implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("DATABASE_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return;
        }
        if (environment.getProperty("TEST_DATASOURCE_DRIVER") != null) {
            return;
        }
        Map<String, Object> aligned = new HashMap<>();
        aligned.put("TEST_DATASOURCE_DRIVER", "org.postgresql.Driver");
        aligned.put("TEST_JPA_DIALECT", "org.hibernate.dialect.PostgreSQLDialect");
        aligned.put("TEST_DDL_AUTO", "validate");
        aligned.put("TEST_FLYWAY_ENABLED", "true");
        environment.getPropertySources().addFirst(new MapPropertySource("testCiDatasourceAlign", aligned));
    }
}
