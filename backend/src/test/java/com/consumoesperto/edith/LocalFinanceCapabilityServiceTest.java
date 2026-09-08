package com.consumoesperto.edith;

import com.consumoesperto.edith.tools.EdithToolRegistry;
import com.consumoesperto.service.WhatsAppCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalFinanceCapabilityServiceTest {

    @Mock EdithToolRegistry toolRegistry;
    @Mock WhatsAppCommandService whatsAppCommandService;
    @Mock CapabilityManifestService capabilityManifestService;

    private LocalFinanceCapabilityService service;

    @BeforeEach
    void setUp() {
        service = new LocalFinanceCapabilityService(toolRegistry, whatsAppCommandService, capabilityManifestService);
    }

    @Test
    void listarCartoesNaoChamaWhatsappNemLlm() {
        when(toolRegistry.allowedTools()).thenReturn(List.of("finance.cards.list"));
        when(toolRegistry.executeForUser(eq("finance.cards.list"), eq(7L), any()))
            .thenReturn(Map.of("cartoes", List.of(Map.of("nome", "Nubank", "banco", "Nu", "limite_disponivel", 1000))));

        Optional<String> out = service.tryExecute(7L, "Lista os meus cartões", "finance.cards.list");
        assertTrue(out.isPresent());
        assertTrue(out.get().contains("Nubank"));
        verify(whatsAppCommandService, never()).processJarvisCommand(any(), any(), any(), any());
    }

    @Test
    void textoLivreNaoResolveCapabilityLocal() {
        Optional<String> out = service.tryExecute(1L, "listar meus cartoes", null);
        assertTrue(out.isEmpty());
        verify(toolRegistry, never()).executeForUser(any(), anyLong(), any());
    }
}
