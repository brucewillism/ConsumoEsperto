package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.dto.TransacaoIngestSnapshot;
import com.consumoesperto.model.AutonomyDecisionLog;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.model.FinancialDomainEvent;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.repository.AutonomyDecisionLogRepository;
import com.consumoesperto.repository.FinancialDomainEventRepository;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra regras determinísticas, policy e (só se necessário) raciocínio.
 * Não grava transação/saldo via repository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialAutonomyEngine {

    private final FinancialAutonomyProperties properties;
    private final FinancialDomainEventRepository eventRepository;
    private final AutonomyDecisionLogRepository decisionLogRepository;
    private final AutonomyPreferenciaService preferenciaService;
    private final AutonomyPolicyService policyService;
    private final ConfidenceEngine confidenceEngine;
    private final MerchantLearningService merchantLearningService;
    private final FinancialReconciliationService reconciliationService;
    private final AutonomyReviewService reviewService;
    private final DuplicateChargeDetectionService duplicateChargeDetectionService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final JarvisProactiveOrchestrator jarvis;

    @Autowired
    @Lazy
    private TransacaoService transacaoService;

    @Transactional
    public void processEvent(Long eventId) {
        if (!properties.isEnabled() || eventId == null) {
            return;
        }
        FinancialDomainEvent existing = eventRepository.findById(eventId).orElse(null);
        if (existing == null) {
            return;
        }
        if (existing.isProcessed()
            || OutboxProcessingStatus.PROCESSED.name().equals(existing.getProcessingStatus())
            || OutboxProcessingStatus.FAILED_FINAL.name().equals(existing.getProcessingStatus())) {
            return;
        }
        LocalDateTime now = AppTimeZone.agora();
        LocalDateTime staleBefore = now.minusMinutes(Math.max(1, properties.getOutboxStaleProcessingMinutes()));
        if (eventRepository.claimForProcessing(eventId, now, staleBefore) == 0) {
            return;
        }
        FinancialDomainEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        try {
            FinancialEventType type = FinancialEventType.valueOf(event.getEventType());
            switch (type) {
                case TRANSACTION_INGESTED, MOBILE_TRANSACTION_RECEIVED, CSV_IMPORTED, PDF_IMPORTED ->
                    handleIngested(event.getUsuarioId(), event.getAggregateId(), event.getId());
                case CATEGORY_CORRECTED, TRANSACTION_UPDATED ->
                    handleCategoryCorrected(event.getUsuarioId(), event.getAggregateId(), event.getId());
                case TRANSACTION_RECONCILED ->
                    logDecision(event.getUsuarioId(), event.getId(), event.getAggregateId(),
                        "RECONCILE", AutonomyActionClass.SAFE_AUTO, BigDecimal.ONE,
                        "RECONCILE_DUPLICATE", "EXECUTED", "evidence attached", false, null);
                default -> log.debug("autonomy_event_ignored type={}", type);
            }
            markProcessed(event);
        } catch (Exception e) {
            log.warn("autonomy_event_failed id={}: {}", eventId, e.getMessage());
            markFailed(event, e);
        }
    }

    public List<FinancialDomainEvent> claimableBatch() {
        LocalDateTime now = AppTimeZone.agora();
        LocalDateTime staleBefore = now.minusMinutes(Math.max(1, properties.getOutboxStaleProcessingMinutes()));
        return eventRepository.findClaimable(now, staleBefore, PageRequest.of(0, 50));
    }

    private void markProcessed(FinancialDomainEvent event) {
        event.setProcessed(true);
        event.setProcessingStatus(OutboxProcessingStatus.PROCESSED.name());
        event.setProcessedAt(AppTimeZone.agora());
        event.setLastErrorCode(null);
        event.setNextRetryAt(null);
        eventRepository.save(event);
    }

    private void markFailed(FinancialDomainEvent event, Exception error) {
        int attempts = event.getAttemptCount();
        int max = Math.max(1, properties.getOutboxMaxAttempts());
        String code = errorCodeOf(error);
        event.setLastErrorCode(code);
        event.setProcessed(false);
        event.setProcessedAt(null);
        if (attempts >= max) {
            event.setProcessingStatus(OutboxProcessingStatus.FAILED_FINAL.name());
            event.setNextRetryAt(null);
            try {
                reviewService.open(event.getUsuarioId(), "OUTBOX", event.getAggregateId(),
                    "Evento de autonomia falhou em definitivo",
                    "event_id=" + event.getId() + " code=" + code,
                    new BigDecimal("0.99"));
            } catch (Exception ignored) {
                log.debug("outbox_final_review_skipped id={}", event.getId());
            }
        } else {
            Duration delay = OutboxBackoff.delayForAttempt(
                attempts, properties.getOutboxRetrySeconds(), properties.getOutboxJitterRatio());
            event.setProcessingStatus(OutboxProcessingStatus.FAILED_RETRYABLE.name());
            event.setNextRetryAt(AppTimeZone.agora().plus(delay));
        }
        eventRepository.save(event);
    }

    private static String errorCodeOf(Exception e) {
        if (e == null) {
            return "UNEXPECTED";
        }
        String name = e.getClass().getSimpleName();
        if (name.toLowerCase().contains("timeout")) {
            return "TIMEOUT";
        }
        if (name.contains("Edith") || (e.getMessage() != null && e.getMessage().toLowerCase().contains("edith"))) {
            return "EDITH_UNAVAILABLE";
        }
        return "UNEXPECTED";
    }

    @Transactional
    public AutonomyDecision handleIngested(Long usuarioId, Long transacaoId, Long eventId) {
        if (!properties.isEnabled() || transacaoId == null) {
            return AutonomyDecision.skipped("disabled");
        }
        AutonomyPreferencia pref = preferenciaService.obterOuCriar(usuarioId);
        AutonomyLevel level = preferenciaService.levelOf(pref);
        TransacaoIngestSnapshot snap = transacaoService.snapshotIngestao(transacaoId, usuarioId);
        OrigemTransacao origem = snap.origem() != null ? snap.origem() : OrigemTransacao.MANUAL;
        reconciliationService.attachOrCreateEvidence(
            usuarioId, transacaoId, origem, snap.externalEventId(), null, BigDecimal.ONE);

        if (pref.isDetectarDuplicatas()) {
            var dups = duplicateChargeDetectionService.findPossibleDuplicates(usuarioId, snap);
            if (!dups.isEmpty()) {
                reviewService.open(usuarioId, "DUPLICATE", transacaoId,
                    "Possível cobrança duplicada",
                    "Valor " + MoedaUtil.nz(snap.valor()) + " em janela curta no mesmo estabelecimento.",
                    new BigDecimal("0.80"));
                notifySafe(usuarioId, JarvisNotificationPriority.ACTION_REQUIRED,
                    "Cobrança duplicada?",
                    "Encontrei duas cobranças de R$ " + MoedaUtil.nz(snap.valor())
                        + " no mesmo estabelecimento em poucos minutos. Deseja verificar?",
                    eventId, transacaoId);
                logDecision(usuarioId, eventId, transacaoId, "POSSIBLE_DUPLICATE_CHARGE",
                    AutonomyActionClass.SAFE_AUTO, new BigDecimal("0.80"),
                    "GENERATE_ALERT", "REVIEW", "duplicate window", false, null);
            }
        }

        AutonomyDecision classification = classifyIfNeeded(usuarioId, transacaoId, snap, pref, level, eventId);

        if (pref.isDetectarAnomalias() && properties.isAnomaly()) {
            List<AnomalyDetectionService.Anomaly> anomalies = anomalyDetectionService.detectar(usuarioId, transacaoId);
            for (AnomalyDetectionService.Anomaly a : anomalies) {
                reviewService.open(usuarioId, "ANOMALY", a.transacaoId(), a.title(), a.detail(), a.confidence());
                JarvisNotificationPriority prio = "SUBSCRIPTION_PRICE_CHANGE".equals(a.code())
                    ? JarvisNotificationPriority.NOTICE
                    : JarvisNotificationPriority.WARNING;
                notifySafe(usuarioId, prio, a.title(), a.detail(), eventId, a.transacaoId());
            }
        }
        return classification;
    }

    @Transactional
    public void handleCategoryCorrected(Long usuarioId, Long transacaoId, Long eventId) {
        if (!properties.isMerchantLearning()) {
            return;
        }
        AutonomyPreferencia pref = preferenciaService.obterOuCriar(usuarioId);
        if (!pref.isAprenderCategorias()) {
            return;
        }
        TransacaoDTO dto = transacaoService.buscarPorId(transacaoId, usuarioId);
        if (dto.getCategoriaId() == null) {
            return;
        }
        merchantLearningService.learnFromUserCorrection(usuarioId, dto.getDescricao(), dto.getCategoriaId());
        logDecision(usuarioId, eventId, transacaoId, "MERCHANT_LEARN",
            AutonomyActionClass.AUTO_WITH_AUDIT, BigDecimal.ONE,
            "UPDATE_MERCHANT_RULE_AFTER_CONFIRM", "EXECUTED",
            "user correction", false, null);
    }

    private AutonomyDecision classifyIfNeeded(
        Long usuarioId,
        Long transacaoId,
        TransacaoIngestSnapshot snap,
        AutonomyPreferencia pref,
        AutonomyLevel level,
        Long eventId
    ) {
        if (snap.categoriaId() != null) {
            return AutonomyDecision.skipped("already-classified");
        }
        String merchant = first(snap.merchantNormalized(), snap.merchantRaw(), snap.descricao());
        Optional<MerchantLearningService.LearnedCategory> learned = merchantLearningService.resolve(usuarioId, merchant);
        if (learned.isEmpty()) {
            reviewService.open(usuarioId, "CATEGORY", transacaoId,
                "Não identifiquei esta compra",
                truncate(merchant) + " · R$ " + MoedaUtil.nz(snap.valor()),
                new BigDecimal("0.45"));
            notifySafe(usuarioId, JarvisNotificationPriority.ACTION_REQUIRED,
                "Classificação pendente",
                "Identifiquei um lançamento de R$ " + MoedaUtil.nz(snap.valor())
                    + " (" + truncate(merchant) + "). Foi uma despesa, transferência ou outra coisa?",
                eventId, transacaoId);
            logDecision(usuarioId, eventId, transacaoId, "CLASSIFY",
                AutonomyActionClass.SAFE_AUTO, new BigDecimal("0.45"),
                "CATEGORIZE_KNOWN_MERCHANT", "NEEDS_REVIEW",
                "unknown merchant", false, null);
            return AutonomyDecision.review("unknown");
        }
        MerchantLearningService.LearnedCategory cat = learned.get();
        ConfidenceBand band = confidenceEngine.band(cat.confidence());
        AutonomyActionType actionType = AutonomyActionType.CATEGORIZE_KNOWN_MERCHANT;
        AutonomyActionClass actionClass = policyService.classify(actionType);
        PolicyOutcome outcome = policyService.decide(actionClass, level, band, pref.isClassificarAuto());
        String reason = reasonOf(cat);
        if (outcome == PolicyOutcome.APPLY) {
            transacaoService.aplicarCategoriaAutonoma(transacaoId, usuarioId, cat.categoriaId());
            if (pref.isAprenderCategorias() && properties.isMerchantLearning()) {
                merchantLearningService.learnFromUserCorrection(usuarioId, merchant, cat.categoriaId());
            }
            if (band == ConfidenceBand.EXECUTE_AND_MARK_REVIEWABLE) {
                reviewService.open(usuarioId, "CATEGORY", transacaoId,
                    "Categoria aplicada — conferir", reason, cat.confidence());
            }
            logDecision(usuarioId, eventId, transacaoId, "CLASSIFY",
                actionClass, cat.confidence(), actionType.name(), "EXECUTED",
                reason, cat.cognitiveUsed(), cat.edithTaskId());
            return new AutonomyDecision("EXECUTED", cat.categoriaId(), cat.confidence(), cat.cognitiveUsed(), reason);
        }
        transacaoService.aplicarCategoriaSugerida(transacaoId, usuarioId, cat.categoriaId());
        reviewService.open(usuarioId, "CATEGORY", transacaoId,
            "Categoria sugerida para revisão",
            reason, cat.confidence());
        if (outcome == PolicyOutcome.FORBIDDEN) {
            logDecision(usuarioId, eventId, transacaoId, "CLASSIFY",
                actionClass, cat.confidence(), actionType.name(), "FORBIDDEN",
                reason, cat.cognitiveUsed(), cat.edithTaskId());
            return AutonomyDecision.review(reason);
        }
        String result = outcome == PolicyOutcome.SUGGEST ? "SUGGESTED" : "NEEDS_REVIEW";
        logDecision(usuarioId, eventId, transacaoId, "CLASSIFY",
            actionClass, cat.confidence(), actionType.name(), result,
            reason, cat.cognitiveUsed(), cat.edithTaskId());
        return outcome == PolicyOutcome.SUGGEST
            ? AutonomyDecision.suggested(cat.categoriaId(), cat.confidence(), reason)
            : AutonomyDecision.review(reason);
    }

    private void logDecision(
        Long usuarioId,
        Long eventId,
        Long transacaoId,
        String decisionType,
        AutonomyActionClass policy,
        BigDecimal confidence,
        String action,
        String result,
        String reason,
        boolean cognitiveUsed,
        String edithTaskId
    ) {
        AutonomyDecisionLog row = new AutonomyDecisionLog();
        row.setUsuarioId(usuarioId);
        row.setEventId(eventId);
        row.setDecisionType(decisionType);
        row.setPolicy(policy.name());
        row.setConfidence(confidence);
        row.setAction(action);
        row.setResult(result);
        row.setRuleCode(action);
        row.setReason(trim(reason, 400));
        row.setCognitiveUsed(cognitiveUsed);
        row.setEdithTaskId(edithTaskId);
        row.setTransacaoId(transacaoId);
        row.setCreatedAt(AppTimeZone.agora());
        decisionLogRepository.save(row);
    }

    private void notifySafe(
        Long usuarioId,
        JarvisNotificationPriority priority,
        String title,
        String message,
        Long financialEventId,
        Long resourceId
    ) {
        try {
            jarvis.notify(usuarioId, priority, title, message, financialEventId, resourceId);
        } catch (Exception e) {
            log.debug("JARVIS indisponível userId={}: {}", usuarioId, e.getMessage());
        }
    }

    private static String reasonOf(MerchantLearningService.LearnedCategory cat) {
        if ("MERCHANT_RULE".equals(cat.origin()) || "RULE".equals(cat.origin())) {
            return "merchant rule source=MERCHANT_RULE";
        }
        if ("CORRECAO".equals(cat.origin())) {
            return "user correction memory";
        }
        if ("EDITH".equals(cat.origin())) {
            return trim("EDITH source=" + EdithSourceActions.TRANSACTION_CLASSIFICATION
                + " conv=" + n(cat.conversationId())
                + " task=" + n(cat.edithTaskId())
                + " req=" + n(cat.requestId()), 400);
        }
        return cat.origin();
    }

    private static String n(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static String first(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String truncate(String v) {
        if (v == null) {
            return "";
        }
        return v.length() <= 80 ? v : v.substring(0, 80);
    }

    private static String trim(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    public record AutonomyDecision(
        String result,
        Long categoriaId,
        BigDecimal confidence,
        boolean cognitiveUsed,
        String reason
    ) {
        static AutonomyDecision skipped(String reason) {
            return new AutonomyDecision("SKIPPED", null, null, false, reason);
        }

        static AutonomyDecision review(String reason) {
            return new AutonomyDecision("NEEDS_REVIEW", null, null, false, reason);
        }

        static AutonomyDecision suggested(Long categoriaId, BigDecimal confidence, String reason) {
            return new AutonomyDecision("SUGGESTED", categoriaId, confidence, false, reason);
        }
    }
}
