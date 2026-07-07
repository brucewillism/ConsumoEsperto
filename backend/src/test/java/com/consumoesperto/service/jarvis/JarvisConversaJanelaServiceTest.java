package com.consumoesperto.service.jarvis;

import com.consumoesperto.config.JarvisPerformanceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarvisConversaJanelaServiceTest {

    private JarvisConversaJanelaService service;

    @BeforeEach
    void setUp() {
        JarvisPerformanceProperties props = new JarvisPerformanceProperties();
        props.setConversaJanelaMaxTrocas(6);
        props.setConversaJanelaTtlSeconds(3600);
        service = new JarvisConversaJanelaService(props);
    }

    @Test
    void historicoMultiTurnoApareceNoBloco() {
        Long userId = 42L;
        service.registrarUsuario(userId, "quanto gastei hoje?");
        service.registrarAssistente(userId, "Hoje você gastou R$ 120.");
        service.registrarUsuario(userId, "e ontem?");
        String bloco = service.montarBlocoHistorico(userId);
        assertTrue(bloco.contains("quanto gastei hoje"));
        assertTrue(bloco.contains("e ontem"));
    }

    @Test
    void historicoVazioSemRegistros() {
        assertFalse(service.montarBlocoHistorico(999L).contains("Usuário:"));
    }
}
