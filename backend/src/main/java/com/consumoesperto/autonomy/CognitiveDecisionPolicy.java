package com.consumoesperto.autonomy;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.config.FinancialAutonomyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * E.D.I.T.H. só para ambiguidade / análise. Nunca para soma, match por ID, saldo ou ownership.
 */
@Component
@RequiredArgsConstructor
public class CognitiveDecisionPolicy {

    private final FinancialAutonomyProperties autonomyProperties;
    private final EdithProperties edithProperties;

    public boolean needsEdith(CognitiveNeed need, boolean localRuleSufficient) {
        if (!autonomyProperties.isEnabled() || !autonomyProperties.isEdith()) {
            return false;
        }
        if (!edithProperties.isEnabled()) {
            return false;
        }
        if (localRuleSufficient) {
            return false;
        }
        return switch (need) {
            case SUM, EXACT_MATCH, KNOWN_RULE, INVOICE_CYCLE, BALANCE, OWNERSHIP -> false;
            case AMBIGUOUS_DESCRIPTION, UNKNOWN_CLASSIFICATION, ANOMALY,
                 ANALYSIS, EXPLANATORY_FORECAST, COMPLEX_COMPARE, SUGGESTION, NATURAL_LANGUAGE
                -> true;
        };
    }

    public enum CognitiveNeed {
        SUM,
        EXACT_MATCH,
        KNOWN_RULE,
        INVOICE_CYCLE,
        BALANCE,
        OWNERSHIP,
        AMBIGUOUS_DESCRIPTION,
        UNKNOWN_CLASSIFICATION,
        ANOMALY,
        ANALYSIS,
        EXPLANATORY_FORECAST,
        COMPLEX_COMPARE,
        SUGGESTION,
        NATURAL_LANGUAGE
    }
}
