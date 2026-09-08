package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.service.EvolutionSessionMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health separado: core/database/edith/whatsapp/assistant. Edith down não marca o core.
 */
@Service
@RequiredArgsConstructor
public class ConsumoRuntimeHealthService {

    private final DataSource dataSource;
    private final EdithProperties edithProperties;
    private final EdithIntegrationService edithIntegrationService;
    private final EvolutionSessionMonitorService evolutionSessionMonitorService;

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("core", "AVAILABLE");
        out.put("database", probeDatabase());
        out.put("edith", probeEdith());
        out.put("whatsapp", probeWhatsapp());
        out.put("assistant", probeAssistant());
        return out;
    }

    private String probeDatabase() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2) ? "AVAILABLE" : "UNAVAILABLE";
        } catch (Exception e) {
            return "UNAVAILABLE";
        }
    }

    private String probeEdith() {
        if (!edithProperties.isEnabled()) {
            return "DISABLED";
        }
        if (edithIntegrationService.isOperational()) {
            return "AVAILABLE";
        }
        return "UNAVAILABLE";
    }

    private String probeWhatsapp() {
        try {
            var h = evolutionSessionMonitorService.obterHealth(false);
            if (h.getFalhasHoje() > 20 && h.getSessoesAtivas() == 0) {
                return "UNAVAILABLE";
            }
            return "AVAILABLE";
        } catch (Exception e) {
            return "UNAVAILABLE";
        }
    }

    private String probeAssistant() {
        if (!edithProperties.isEnabled()) {
            return "LOCAL";
        }
        if (edithIntegrationService.isOperational()) {
            return "ONLINE";
        }
        return edithProperties.isFallbackEnabled() ? "DEGRADED" : "EDITH_UNAVAILABLE";
    }
}
