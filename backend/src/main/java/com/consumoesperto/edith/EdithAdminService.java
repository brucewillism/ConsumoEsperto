package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toggle runtime da E.D.I.T.H. (admin). Ao reiniciar o backend, volta ao valor de {@code EDITH_ENABLED} no ambiente.
 */
@Service
@Slf4j
public class EdithAdminService {

    @Getter
    private final boolean envDefaultEnabled;

    private final EdithProperties properties;
    private final EdithIntegrationService integrationService;

    public EdithAdminService(EdithProperties properties, EdithIntegrationService integrationService) {
        this.properties = properties;
        this.integrationService = integrationService;
        this.envDefaultEnabled = properties.isEnabled();
    }

    public Map<String, Object> adminStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", properties.isEnabled());
        out.put("state", resolveState());
        out.put("envDefaultEnabled", envDefaultEnabled);
        out.put("configured", integrationService.isOperational() || isPartiallyConfigured());
        out.put("baseUrlConfigured", properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank());
        out.put("apiKeyConfigured", properties.getApiKey() != null && !properties.getApiKey().isBlank());
        return out;
    }

    public Map<String, Object> setEnabled(boolean enabled) {
        if (enabled && !isPartiallyConfigured()) {
            throw new EdithException(
                EdithErrorCode.EDITH_UNAVAILABLE,
                "Configure EDITH_BASE_URL e EDITH_API_KEY no servidor antes de ligar a E.D.I.T.H."
            );
        }
        properties.setEnabled(enabled);
        log.info("edith_admin_toggle enabled={} by=admin env_default={}", enabled, envDefaultEnabled);
        return adminStatus();
    }

    private String resolveState() {
        if (!properties.isEnabled()) {
            return "DISABLED";
        }
        return integrationService.isOperational() ? "AVAILABLE" : "UNAVAILABLE";
    }

    private boolean isPartiallyConfigured() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
            && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }
}
