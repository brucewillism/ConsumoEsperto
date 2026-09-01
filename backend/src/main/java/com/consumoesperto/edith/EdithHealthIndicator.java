package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.client.EdithHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Health específico E.D.I.T.H. — não derruba o health principal quando UNAVAILABLE.
 */
@Component("edith")
@RequiredArgsConstructor
public class EdithHealthIndicator implements HealthIndicator {

    private final EdithProperties properties;
    private final EdithHttpClient httpClient;

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("state", "DISABLED").build();
        }
        if (!httpClient.isConfigured()) {
            return Health.up().withDetail("state", "UNAVAILABLE").withDetail("reason", "missing_config").build();
        }
        try {
            ResponseEntity<String> probe = httpClient.healthProbe();
            if (probe.getStatusCode() == HttpStatus.OK) {
                return Health.up().withDetail("state", "AVAILABLE").build();
            }
            return Health.up().withDetail("state", "UNAVAILABLE").withDetail("http", probe.getStatusCodeValue()).build();
        } catch (Exception e) {
            return Health.up().withDetail("state", "UNAVAILABLE").withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
