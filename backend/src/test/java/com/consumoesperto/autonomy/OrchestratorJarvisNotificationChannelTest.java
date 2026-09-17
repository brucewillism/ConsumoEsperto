package com.consumoesperto.autonomy;

import com.consumoesperto.model.NotificacaoEventoTipo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OrchestratorJarvisNotificationChannelTest {

    @Test
    void actionRequiredNaoViraConferenciaNotas() {
        assertEquals(NotificacaoEventoTipo.ACTION_REQUIRED,
            OrchestratorJarvisNotificationChannel.mapEvento(JarvisNotificationPriority.ACTION_REQUIRED));
    }

    @Test
    void pixJoaoEMariaNaoColapsam() {
        String joao = OrchestratorJarvisNotificationChannel.idempotencyHash(
            1L, NotificacaoEventoTipo.ACTION_REQUIRED, 10L, 101L, "PIX João 350");
        String maria = OrchestratorJarvisNotificationChannel.idempotencyHash(
            1L, NotificacaoEventoTipo.ACTION_REQUIRED, 11L, 102L, "PIX Maria 350");
        assertNotEquals(joao, maria);
    }
}
