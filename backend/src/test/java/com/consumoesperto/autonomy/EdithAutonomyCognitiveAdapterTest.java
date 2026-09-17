package com.consumoesperto.autonomy;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.edith.CognitiveGatewaySelector;
import com.consumoesperto.edith.CognitiveRequest;
import com.consumoesperto.edith.CognitiveResponse;
import com.consumoesperto.edith.EdithCognitiveGateway;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.repository.CategoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EdithAutonomyCognitiveAdapterTest {

    @Test
    void naoChamaEdithQuandoGatewayNaoOperacional() {
        FinancialAutonomyProperties autonomy = new FinancialAutonomyProperties();
        autonomy.setEdith(true);
        EdithProperties edith = new EdithProperties();
        edith.setEnabled(true);
        CognitiveGatewaySelector selector = mock(CognitiveGatewaySelector.class);
        when(selector.usesEdith()).thenReturn(false);
        EdithCognitiveGateway gateway = mock(EdithCognitiveGateway.class);
        EdithAutonomyCognitiveAdapter adapter = new EdithAutonomyCognitiveAdapter(
            edith, autonomy, selector, gateway, mock(CategoriaRepository.class), new ObjectMapper());
        Optional<AutonomyCognitivePort.ClassificationHint> out = adapter.classifyMerchant(1L, "SHELL", "SHELL");
        assertTrue(out.isEmpty());
        verify(gateway, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveCategoriaPorNomeEPropagaIdsDaTask() {
        FinancialAutonomyProperties autonomy = new FinancialAutonomyProperties();
        autonomy.setEdith(true);
        EdithProperties edith = new EdithProperties();
        edith.setEnabled(true);
        CognitiveGatewaySelector selector = mock(CognitiveGatewaySelector.class);
        when(selector.usesEdith()).thenReturn(true);
        EdithCognitiveGateway gateway = mock(EdithCognitiveGateway.class);
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        Categoria combustivel = new Categoria();
        combustivel.setId(44L);
        combustivel.setNome("Combustível");
        when(categorias.findByUsuarioIdOrderByNome(1L)).thenReturn(List.of(combustivel));
        when(gateway.send(org.mockito.ArgumentMatchers.any())).thenReturn(CognitiveResponse.builder()
            .conversationId("conv_real")
            .taskId("task_real")
            .requestId("req_real")
            .resultText("{\"category\":\"Combustivel\",\"confidence\":0.91}")
            .build());
        EdithAutonomyCognitiveAdapter adapter = new EdithAutonomyCognitiveAdapter(
            edith, autonomy, selector, gateway, categorias, new ObjectMapper());

        AutonomyCognitivePort.ClassificationHint hint = adapter.classifyMerchant(1L, "POSTO SHELL", "POSTO SHELL")
            .orElseThrow();

        assertEquals(44L, hint.categoriaId());
        assertEquals("Combustível", hint.categoriaNome());
        assertEquals(0, new BigDecimal("0.91").compareTo(hint.confidence()));
        assertEquals("task_real", hint.edithTaskId());
        assertEquals("conv_real", hint.conversationId());
        assertEquals("req_real", hint.requestId());
        ArgumentCaptor<CognitiveRequest> cap = ArgumentCaptor.forClass(CognitiveRequest.class);
        verify(gateway).send(cap.capture());
        assertEquals(EdithSourceActions.TRANSACTION_CLASSIFICATION, cap.getValue().getSourceAction());
        assertTrue(cap.getValue().getTimeoutMs() > 0);
        assertTrue(cap.getValue().isAwaitCompletion());
    }
}
