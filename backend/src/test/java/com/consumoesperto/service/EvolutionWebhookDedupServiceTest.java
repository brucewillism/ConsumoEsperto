package com.consumoesperto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvolutionWebhookDedupServiceTest {

    @Test
    void montarChave_usaMessageKeyIdQuandoPresente() {
        String key = EvolutionWebhookDedupService.montarChave("inst1", "ABC123", "+5511@s.whatsapp.net", false, "oi");
        assertEquals("inst1|id|ABC123", key);
    }

    @Test
    void montarChave_fallbackSemIdIncluiJidEHash() {
        String key = EvolutionWebhookDedupService.montarChave("inst1", "", "+5511@s.whatsapp.net", false, "teste");
        assertTrue(key.startsWith("inst1|noid|+5511@s.whatsapp.net|false|"));
    }
}
