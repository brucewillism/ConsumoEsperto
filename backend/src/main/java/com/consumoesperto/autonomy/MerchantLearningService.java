package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.mobilecapture.service.MerchantCategoryRuleService;
import com.consumoesperto.service.jarvis.CategoriaCorrecaoMemoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantLearningService {

    private final MerchantCategoryRuleService merchantCategoryRuleService;
    private final CategoriaCorrecaoMemoriaService categoriaCorrecaoMemoriaService;
    private final AutonomyCognitivePort cognitivePort;
    private final CognitiveDecisionPolicy cognitiveDecisionPolicy;
    private final FinancialAutonomyProperties properties;

    public Optional<LearnedCategory> resolve(Long usuarioId, String merchantOrDescription) {
        if (!properties.isMerchantLearning() || merchantOrDescription == null || merchantOrDescription.isBlank()) {
            return Optional.empty();
        }
        Optional<MerchantCategoryRuleService.CategorySuggestion> rule =
            merchantCategoryRuleService.suggest(usuarioId, merchantOrDescription);
        if (rule.isPresent()) {
            return Optional.of(new LearnedCategory(
                rule.get().categoryId(),
                rule.get().confidence(),
                "MERCHANT_RULE",
                false,
                null,
                null,
                null
            ));
        }
        Optional<Long> correcao = categoriaCorrecaoMemoriaService.sugerirCategoriaPorCorrecao(
            usuarioId, merchantOrDescription);
        if (correcao.isPresent()) {
            return Optional.of(new LearnedCategory(correcao.get(), BigDecimal.ONE, "CORRECAO", false, null));
        }
        boolean askEdith = cognitiveDecisionPolicy.needsEdith(
            CognitiveDecisionPolicy.CognitiveNeed.UNKNOWN_CLASSIFICATION, false);
        if (askEdith) {
            try {
                Optional<AutonomyCognitivePort.ClassificationHint> hint =
                    cognitivePort.classifyMerchant(usuarioId, merchantOrDescription, merchantOrDescription);
                if (hint.isPresent() && hint.get().categoriaId() != null) {
                    return Optional.of(new LearnedCategory(
                        hint.get().categoriaId(),
                        hint.get().confidence() != null ? hint.get().confidence() : BigDecimal.ZERO,
                        "EDITH",
                        true,
                        hint.get().edithTaskId(),
                        hint.get().conversationId(),
                        hint.get().requestId()
                    ));
                }
            } catch (Exception e) {
                log.debug("E.D.I.T.H. classificação indisponível userId={}: {}", usuarioId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void learnFromUserCorrection(Long usuarioId, String merchantOrDescription, Long categoriaId) {
        if (!properties.isMerchantLearning() || categoriaId == null) {
            return;
        }
        categoriaCorrecaoMemoriaService.registrarCorrecaoCategoria(usuarioId, merchantOrDescription, categoriaId);
        try {
            merchantCategoryRuleService.saveUserRule(usuarioId, merchantOrDescription, categoriaId);
        } catch (Exception e) {
            log.debug("Regra merchant não gravada userId={}: {}", usuarioId, e.getMessage());
        }
    }

    public record LearnedCategory(
        Long categoriaId,
        BigDecimal confidence,
        String origin,
        boolean cognitiveUsed,
        String edithTaskId,
        String conversationId,
        String requestId
    ) {
        public LearnedCategory(
            Long categoriaId,
            BigDecimal confidence,
            String origin,
            boolean cognitiveUsed,
            String edithTaskId
        ) {
            this(categoriaId, confidence, origin, cognitiveUsed, edithTaskId, null, null);
        }
    }
}
