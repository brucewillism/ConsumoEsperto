package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenSuppressorServiceTest {

    @Test
    void fastStrategySkipsOllama() {
        assertFalse(TokenSuppressorService.shouldUseOllamaForStrategy("fast"));
        assertFalse(TokenSuppressorService.shouldUseOllamaForStrategy("code-focused"));
    }

    @Test
    void heavyStrategiesUseOllama() {
        assertTrue(TokenSuppressorService.shouldUseOllamaForStrategy("ultra"));
        assertTrue(TokenSuppressorService.shouldUseOllamaForStrategy("balanced"));
        assertTrue(TokenSuppressorService.shouldUseOllamaForStrategy("aggressive"));
    }
}
