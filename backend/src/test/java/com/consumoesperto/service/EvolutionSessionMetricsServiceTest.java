package com.consumoesperto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvolutionSessionMetricsServiceTest {

    private EvolutionSessionMetricsService service;

    @BeforeEach
    void setUp() {
        service = new EvolutionSessionMetricsService();
        ReflectionTestUtils.setField(service, "limiteDesconexoesInstavel", 3);
        ReflectionTestUtils.setField(service, "limiteReconexoesInstavel", 5);
        ReflectionTestUtils.setField(service, "inatividadeInstavelMinutos", 45);
    }

    @Test
    void contabilizaMensagensEnviadasERecebidas() {
        service.recordIncoming("ce-u1");
        service.recordOutgoing("ce-u1");
        service.recordOutgoing("ce-u1");

        var snap = service.snapshot("ce-u1");
        assertEquals(2, snap.mensagensEnviadas());
        assertEquals(1, snap.mensagensRecebidas());
        assertEquals(3, service.totalMensagensHoje());
    }

    @Test
    void marcaInstabilidadeAposMultiplasDesconexoes() {
        for (int i = 0; i < 3; i++) {
            service.recordDisconnect("ce-u2", "close");
        }
        var snap = service.snapshot("ce-u2");
        assertTrue(snap.instavel());
        assertEquals(3, snap.desconexoesHoje());
    }

    @Test
    void reconexoesSomamNoDia() {
        service.recordReconnect("ce-u3", "watchdog");
        service.recordReconnect("ce-u3", "watchdog-restart");
        assertEquals(2, service.totalReconexoesHoje());
    }

    @Test
    void contabilizaFalhasELatencia() {
        service.recordSendFailure("ce-u4");
        service.recordApiLatency("ce-u4", 120);
        service.recordApiLatency("ce-u4", 280);

        var snap = service.snapshot("ce-u4");
        assertEquals(1, snap.falhasHoje());
        assertTrue(snap.latenciaMediaMs() >= 120);
        assertEquals(1, service.totalFalhasHoje());
    }
}
