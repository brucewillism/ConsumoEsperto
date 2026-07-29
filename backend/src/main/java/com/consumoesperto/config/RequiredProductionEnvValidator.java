package com.consumoesperto.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Falha na inicialização quando variáveis obrigatórias não estão definidas
 * (exceto no profile {@code test}, que usa H2 em memória).
 */
@Component
public class RequiredProductionEnvValidator {

    private final Environment environment;

    public RequiredProductionEnvValidator(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (isTestProfile()) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url", "");
        if (url.contains("h2:")) {
            return;
        }
        List<String> missing = new ArrayList<>();
        check(missing, "spring.datasource.url", url);
        check(missing, "spring.datasource.username", environment.getProperty("spring.datasource.username"));
        check(missing, "spring.datasource.password", environment.getProperty("spring.datasource.password"));
        check(missing, "jwt.secret", environment.getProperty("jwt.secret"));
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Configurações obrigatórias ausentes: " + String.join(", ", missing)
                + ". Defina DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD e JWT_SECRET (.env ou variáveis)."
            );
        }
        String jwt = environment.getProperty("jwt.secret", "");
        if (jwt.length() < 32) {
            throw new IllegalStateException("JWT_SECRET deve ter pelo menos 32 caracteres.");
        }
    }

    private static void check(List<String> missing, String key, String val) {
        if (val == null || val.isBlank() || val.contains("${")) {
            missing.add(key);
        }
    }

    private boolean isTestProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
