package com.consumoesperto.edith;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdithBaseUrlTest {

    @Test
    void porta5173EFrontendNaoApi() {
        assertTrue(EdithBaseUrl.looksLikeFrontend("http://localhost:5173/"));
        assertTrue(EdithBaseUrl.looksLikeFrontend("http://127.0.0.1:4173"));
        assertFalse(EdithBaseUrl.looksLikeFrontend("http://127.0.0.1:8000"));
        assertFalse(EdithBaseUrl.looksLikeFrontend("http://edith_api:8080"));
        assertEquals("http://localhost:8000", EdithBaseUrl.suggestedApiUrl("http://localhost:5173/"));
    }
}
