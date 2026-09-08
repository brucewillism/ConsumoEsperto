package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.edith.client.EdithApiModels;
import com.consumoesperto.edith.client.EdithHttpClient;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.EdithConversationLink;
import com.consumoesperto.repository.EdithConversationLinkRepository;
import com.consumoesperto.repository.EdithTaskLinkRepository;
import com.consumoesperto.service.FaturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdithApplicationContextOwnershipTest {

    @Mock EdithProperties properties;
    @Mock EdithHttpClient httpClient;
    @Mock EdithContextRefService contextRefService;
    @Mock EdithConversationLinkRepository conversationLinkRepository;
    @Mock EdithTaskLinkRepository taskLinkRepository;
    @Mock FaturaService faturaService;

    private EdithIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new EdithIntegrationService(
            properties, httpClient, contextRefService, conversationLinkRepository, taskLinkRepository, faturaService);
        when(properties.isEnabled()).thenReturn(true);
        when(httpClient.isConfigured()).thenReturn(true);
        when(httpClient.isCircuitOpen()).thenReturn(false);
        when(properties.getApplicationId()).thenReturn("consumo-esperto");
        when(properties.getProject()).thenReturn("CONSUMO_ESPERTO");
        when(contextRefService.generate()).thenReturn("ctx-owned");
        when(conversationLinkRepository.findByEdithConversationIdAndUsuarioId("conv-1", 1L))
            .thenReturn(Optional.of(new EdithConversationLink(1L, "conv-1")));
        when(taskLinkRepository.findByUsuarioIdAndClientRequestId(eq(1L), any())).thenReturn(Optional.empty());
        when(taskLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void entityIdDeFaturaAlheiaNaoEntraNoContexto() {
        when(faturaService.buscarPorId(99L, 1L)).thenThrow(new ResourceNotFoundException("nao"));
        ArgumentCaptor<EdithApiModels.MessageSendRequest> cap =
            ArgumentCaptor.forClass(EdithApiModels.MessageSendRequest.class);
        when(httpClient.sendMessage(eq("conv-1"), cap.capture())).thenAnswer(inv -> {
            EdithApiModels.MessageSubmission sub = new EdithApiModels.MessageSubmission();
            sub.setTaskId("task-1");
            sub.setStatus("QUEUED");
            return sub;
        });

        service.sendMessage(1L, "conv-1", "resumo", "consumo.chat", "c-1", CognitiveRequest.builder()
            .usuarioId(1L)
            .screen("invoices")
            .entityType("invoice")
            .entityId("99")
            .build());

        Map<String, Object> ctx = cap.getValue().getContext();
        assertEquals("consumo-esperto", ctx.get("application_id"));
        assertEquals("invoices", ctx.get("screen"));
        assertFalse(ctx.containsKey("entity_id"));
        assertFalse(ctx.containsKey("entity_type"));
    }

    @Test
    void entityIdProprioEEnviadoAposOwnership() {
        when(faturaService.buscarPorId(123L, 1L)).thenReturn(new FaturaDTO());
        ArgumentCaptor<EdithApiModels.MessageSendRequest> cap =
            ArgumentCaptor.forClass(EdithApiModels.MessageSendRequest.class);
        when(httpClient.sendMessage(eq("conv-1"), cap.capture())).thenAnswer(inv -> {
            EdithApiModels.MessageSubmission sub = new EdithApiModels.MessageSubmission();
            sub.setTaskId("task-2");
            sub.setStatus("QUEUED");
            return sub;
        });

        service.sendMessage(1L, "conv-1", "resumo", "consumo.chat", "c-2", CognitiveRequest.builder()
            .usuarioId(1L)
            .screen("invoices")
            .entityType("invoice")
            .entityId("123")
            .build());

        Map<String, Object> ctx = cap.getValue().getContext();
        assertEquals("invoice", ctx.get("entity_type"));
        assertEquals("123", ctx.get("entity_id"));
    }
}
