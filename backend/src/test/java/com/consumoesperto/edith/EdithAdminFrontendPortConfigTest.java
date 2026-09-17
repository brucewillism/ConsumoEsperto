package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdithAdminFrontendPortConfigTest {

    @Test
    void porta5173NaoContaComoAvailableMesmoConfigurado() {
        EdithProperties properties = new EdithProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:5173");
        properties.setApiKey("not-a-secret-for-test");
        EdithIntegrationService integration = mock(EdithIntegrationService.class);
        when(integration.isOperational()).thenReturn(false);
        when(integration.isLive()).thenReturn(false);
        EdithAdminService admin = new EdithAdminService(properties, integration);

        var status = admin.adminStatus();
        assertEquals(true, status.get("configured"));
        assertEquals(true, status.get("frontendPortMisconfigured"));
        assertEquals("http://localhost:8000", status.get("suggestedApiUrl"));
        assertEquals("UNAVAILABLE", status.get("state"));
        assertFalse(Boolean.TRUE.equals(status.get("live")));
        assertTrue(EdithBaseUrl.looksLikeFrontend(properties.getBaseUrl()));
    }
}
