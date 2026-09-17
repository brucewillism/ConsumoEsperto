package com.consumoesperto.autonomy;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.dto.TransacaoIngestSnapshot;
import com.consumoesperto.mobilecapture.service.MerchantCategoryRuleService;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.repository.AutonomyDecisionLogRepository;
import com.consumoesperto.repository.FinancialDomainEventRepository;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.service.jarvis.CategoriaCorrecaoMemoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialAutonomyEngineTest {

    @Mock private FinancialDomainEventRepository eventRepository;
    @Mock private AutonomyDecisionLogRepository decisionLogRepository;
    @Mock private AutonomyPreferenciaService preferenciaService;
    @Mock private MerchantCategoryRuleService ruleService;
    @Mock private CategoriaCorrecaoMemoriaService correcaoMemoriaService;
    @Mock private AutonomyCognitivePort cognitivePort;
    @Mock private FinancialReconciliationService reconciliationService;
    @Mock private AutonomyReviewService reviewService;
    @Mock private DuplicateChargeDetectionService duplicateChargeDetectionService;
    @Mock private AnomalyDetectionService anomalyDetectionService;
    @Mock private JarvisProactiveOrchestrator jarvis;
    @Mock private TransacaoService transacaoService;

    private FinancialAutonomyProperties properties;
    private FinancialAutonomyEngine engine;
    private AutonomyPreferencia prefs;

    @BeforeEach
    void setup() {
        properties = new FinancialAutonomyProperties();
        properties.setEnabled(true);
        properties.setEdith(true);
        properties.setMerchantLearning(true);
        properties.setReconciliation(true);
        properties.setAnomaly(true);
        EdithProperties edith = new EdithProperties();
        edith.setEnabled(true);
        CognitiveDecisionPolicy cognitivePolicy = new CognitiveDecisionPolicy(properties, edith);
        MerchantLearningService learning = new MerchantLearningService(
            ruleService, correcaoMemoriaService, cognitivePort, cognitivePolicy, properties);
        engine = new FinancialAutonomyEngine(
            properties,
            eventRepository,
            decisionLogRepository,
            preferenciaService,
            new AutonomyPolicyService(),
            new ConfidenceEngine(properties),
            learning,
            reconciliationService,
            reviewService,
            duplicateChargeDetectionService,
            anomalyDetectionService,
            jarvis
        );
        ReflectionTestUtils.setField(engine, "transacaoService", transacaoService);

        prefs = new AutonomyPreferencia();
        prefs.setUsuarioId(1L);
        prefs.setNivel(AutonomyLevel.AUTONOMOUS_SAFE.name());
        prefs.setClassificarAuto(true);
        prefs.setAprenderCategorias(true);
        prefs.setDetectarDuplicatas(true);
        prefs.setDetectarAnomalias(false);
        when(preferenciaService.obterOuCriar(1L)).thenReturn(prefs);
        when(preferenciaService.levelOf(prefs)).thenReturn(AutonomyLevel.AUTONOMOUS_SAFE);
        when(duplicateChargeDetectionService.findPossibleDuplicates(any(), any())).thenReturn(List.of());
        when(anomalyDetectionService.detectar(anyLong(), any())).thenReturn(List.of());
        when(decisionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shellEdithAltaConfiancaExecutaSemInterromperJarvis() {
        when(transacaoService.snapshotIngestao(10L, 1L)).thenReturn(snap(10L, null));
        when(ruleService.suggest(1L, "POSTO SHELL")).thenReturn(Optional.empty());
        when(correcaoMemoriaService.sugerirCategoriaPorCorrecao(1L, "POSTO SHELL")).thenReturn(Optional.empty());
        when(cognitivePort.classifyMerchant(1L, "POSTO SHELL", "POSTO SHELL"))
            .thenReturn(Optional.of(new AutonomyCognitivePort.ClassificationHint(
                99L, "Combustível", new BigDecimal("0.96"), "task-1")));

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 10L, 5L);

        assertEquals("EXECUTED", d.result());
        assertEquals(99L, d.categoriaId());
        assertTrue(d.cognitiveUsed());
        verify(transacaoService).aplicarCategoriaAutonoma(10L, 1L, 99L);
        verify(jarvis, never()).notify(eq(1L), eq(JarvisNotificationPriority.ACTION_REQUIRED), anyString(), anyString());
        verify(reconciliationService).attachOrCreateEvidence(
            eq(1L), eq(10L), eq(OrigemTransacao.IOS_WALLET), any(), any(), any());
    }

    @Test
    void pixAmbiguoVaiParaRevisaoEJarvisPergunta() {
        when(transacaoService.snapshotIngestao(11L, 1L)).thenReturn(new TransacaoIngestSnapshot(
            11L, OrigemTransacao.PIX, "JOAO SILVA", "JOAO SILVA", "PIX JOAO SILVA",
            null, new BigDecimal("350"), LocalDateTime.now(), null, 3L, null, null));
        when(ruleService.suggest(anyLong(), anyString())).thenReturn(Optional.empty());
        when(correcaoMemoriaService.sugerirCategoriaPorCorrecao(anyLong(), anyString())).thenReturn(Optional.empty());
        when(cognitivePort.classifyMerchant(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 11L, 6L);

        assertEquals("NEEDS_REVIEW", d.result());
        verify(transacaoService, never()).aplicarCategoriaAutonoma(anyLong(), anyLong(), anyLong());
        verify(reviewService).open(eq(1L), eq("CATEGORY"), eq(11L), anyString(), anyString(), any());
        verify(jarvis).notify(eq(1L), eq(JarvisNotificationPriority.ACTION_REQUIRED), anyString(), anyString(), any(), any());
    }

    @Test
    void correcaoDoUsuarioAprendeEProximaNaoUsaEdith() {
        when(transacaoService.buscarPorId(10L, 1L)).thenAnswer(inv -> {
            TransacaoDTO dto = new TransacaoDTO();
            dto.setId(10L);
            dto.setDescricao("POSTO SHELL");
            dto.setCategoriaId(7L);
            return dto;
        });
        engine.handleCategoryCorrected(1L, 10L, 8L);
        verify(correcaoMemoriaService).registrarCorrecaoCategoria(1L, "POSTO SHELL", 7L);
        verify(ruleService).saveUserRule(1L, "POSTO SHELL", 7L);

        when(transacaoService.snapshotIngestao(20L, 1L)).thenReturn(snap(20L, null));
        when(ruleService.suggest(1L, "POSTO SHELL")).thenReturn(Optional.of(
            new MerchantCategoryRuleService.CategorySuggestion(7L, BigDecimal.ONE, "MERCHANT_RULE")));

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 20L, 9L);
        assertEquals("EXECUTED", d.result());
        assertFalse(d.cognitiveUsed());
        verify(cognitivePort, never()).classifyMerchant(anyLong(), anyString(), anyString());
        verify(transacaoService).aplicarCategoriaAutonoma(20L, 1L, 7L);
    }

    @Test
    void edithIndisponivelNaoBloqueiaCore() {
        when(transacaoService.snapshotIngestao(10L, 1L)).thenReturn(snap(10L, null));
        when(ruleService.suggest(anyLong(), anyString())).thenReturn(Optional.empty());
        when(correcaoMemoriaService.sugerirCategoriaPorCorrecao(anyLong(), anyString())).thenReturn(Optional.empty());
        when(cognitivePort.classifyMerchant(anyLong(), anyString(), anyString()))
            .thenThrow(new RuntimeException("edith down"));

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 10L, 5L);
        assertEquals("NEEDS_REVIEW", d.result());
        verify(transacaoService, never()).aplicarCategoriaAutonoma(anyLong(), anyLong(), anyLong());
    }

    @Test
    void jarvisOfflineNaoImpedeClassificacao() {
        when(jarvis.notify(anyLong(), any(), anyString(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("whatsapp down"));
        when(transacaoService.snapshotIngestao(10L, 1L)).thenReturn(snap(10L, null));
        when(ruleService.suggest(anyLong(), anyString())).thenReturn(Optional.empty());
        when(correcaoMemoriaService.sugerirCategoriaPorCorrecao(anyLong(), anyString())).thenReturn(Optional.empty());
        when(cognitivePort.classifyMerchant(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 10L, 5L);
        assertEquals("NEEDS_REVIEW", d.result());
        verify(reviewService).open(eq(1L), eq("CATEGORY"), eq(10L), anyString(), anyString(), any());
    }

    @Test
    void nivelManualNaoExecutaMesmoComAltaConfianca() {
        prefs.setNivel(AutonomyLevel.MANUAL.name());
        when(preferenciaService.levelOf(prefs)).thenReturn(AutonomyLevel.MANUAL);
        when(transacaoService.snapshotIngestao(10L, 1L)).thenReturn(snap(10L, null));
        when(ruleService.suggest(1L, "POSTO SHELL")).thenReturn(Optional.of(
            new MerchantCategoryRuleService.CategorySuggestion(99L, new BigDecimal("0.99"), "MERCHANT_RULE")));

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 10L, 5L);
        assertEquals("SUGGESTED", d.result());
        verify(transacaoService, never()).aplicarCategoriaAutonoma(anyLong(), anyLong(), anyLong());
        verify(transacaoService).aplicarCategoriaSugerida(10L, 1L, 99L);
    }

    @Test
    void assistedComRegraLocalAplica() {
        prefs.setNivel(AutonomyLevel.ASSISTED.name());
        when(preferenciaService.levelOf(prefs)).thenReturn(AutonomyLevel.ASSISTED);
        when(transacaoService.snapshotIngestao(10L, 1L)).thenReturn(snap(10L, null));
        when(ruleService.suggest(1L, "POSTO SHELL")).thenReturn(Optional.of(
            new MerchantCategoryRuleService.CategorySuggestion(99L, new BigDecimal("0.99"), "MERCHANT_RULE")));

        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 10L, 5L);
        assertEquals("EXECUTED", d.result());
        verify(transacaoService).aplicarCategoriaAutonoma(10L, 1L, 99L);
    }

    @Test
    void ingestaoJaClassificadaNaoReaplica() {
        when(transacaoService.snapshotIngestao(10L, 1L)).thenReturn(snap(10L, 5L));
        FinancialAutonomyEngine.AutonomyDecision d = engine.handleIngested(1L, 10L, 5L);
        assertEquals("SKIPPED", d.result());
        verify(transacaoService, never()).aplicarCategoriaAutonoma(anyLong(), anyLong(), anyLong());
    }

    @Test
    void outboxFalhaFicaRetryableNaoProcessed() {
        com.consumoesperto.model.FinancialDomainEvent pending = new com.consumoesperto.model.FinancialDomainEvent();
        pending.setId(8L);
        pending.setUsuarioId(1L);
        pending.setEventType("TRANSACTION_INGESTED");
        pending.setAggregateId(10L);
        pending.setProcessed(false);
        pending.setProcessingStatus("PENDING");
        pending.setAttemptCount(0);
        com.consumoesperto.model.FinancialDomainEvent claimed = new com.consumoesperto.model.FinancialDomainEvent();
        claimed.setId(8L);
        claimed.setUsuarioId(1L);
        claimed.setEventType("TRANSACTION_INGESTED");
        claimed.setAggregateId(10L);
        claimed.setProcessed(false);
        claimed.setProcessingStatus("PROCESSING");
        claimed.setAttemptCount(1);
        when(eventRepository.findById(8L)).thenReturn(Optional.of(pending), Optional.of(claimed));
        when(eventRepository.claimForProcessing(any(), any(), any())).thenReturn(1);
        when(transacaoService.snapshotIngestao(10L, 1L)).thenThrow(new RuntimeException("boom"));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        engine.processEvent(8L);

        org.mockito.ArgumentCaptor<com.consumoesperto.model.FinancialDomainEvent> cap =
            org.mockito.ArgumentCaptor.forClass(com.consumoesperto.model.FinancialDomainEvent.class);
        verify(eventRepository, times(1)).save(cap.capture());
        assertEquals("FAILED_RETRYABLE", cap.getValue().getProcessingStatus());
        assertFalse(cap.getValue().isProcessed());
    }

    private static TransacaoIngestSnapshot snap(Long id, Long categoriaId) {
        return new TransacaoIngestSnapshot(
            id, OrigemTransacao.IOS_WALLET, "POSTO SHELL", "POSTO SHELL", "POSTO SHELL",
            categoriaId, new BigDecimal("89.90"), LocalDateTime.now(), 2L, null, "evt-1", "fp-1");
    }
}
