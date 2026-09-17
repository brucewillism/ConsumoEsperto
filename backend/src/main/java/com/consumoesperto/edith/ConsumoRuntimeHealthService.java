package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.config.MobileCaptureProperties;
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
    private final MobileCaptureProperties mobileCaptureProperties;
    private final FinancialAutonomyProperties autonomyProperties;

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("core", "AVAILABLE");
        out.put("coreFinanceiro", probeDatabase());
        out.put("database", probeDatabase());
        out.put("edith", probeEdith());
        out.put("edithFrontendPortMisconfigured", EdithBaseUrl.looksLikeFrontend(edithProperties.getBaseUrl()));
        out.put("edithSuggestedApiUrl", EdithBaseUrl.suggestedApiUrl(edithProperties.getBaseUrl()));
        out.put("jarvis", probeWhatsapp());
        out.put("whatsapp", probeWhatsapp());
        out.put("assistant", probeAssistant());
        out.put("tokenSuppressor", probeTokenSuppressor());
        out.put("mobileCapture", mobileCaptureProperties.isEnabled() ? "ENABLED" : "DISABLED");
        out.put("autonomy", autonomyProperties.isEnabled() ? "ENABLED" : "DISABLED");
        return out;
    }

    private String probeTokenSuppressor() {
        if (!edithProperties.isEnabled()) {
            return "N/A";
        }
        if (edithIntegrationService.isOperational()) {
            return "VIA_EDITH";
        }
        return "UNAVAILABLE_NON_BLOCKING";
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
        if (EdithBaseUrl.looksLikeFrontend(edithProperties.getBaseUrl())) {
            return "UNAVAILABLE";
        }
        if (edithIntegrationService.isLive()) {
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
