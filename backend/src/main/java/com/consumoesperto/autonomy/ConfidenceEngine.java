package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ConfidenceEngine {

    private final FinancialAutonomyProperties properties;

    public ConfidenceBand band(BigDecimal confidence) {
        if (confidence == null) {
            return ConfidenceBand.DO_NOT_EXECUTE;
        }
        if (confidence.compareTo(properties.getAutoExecuteMin()) >= 0) {
            return ConfidenceBand.AUTO_EXECUTE;
        }
        if (confidence.compareTo(properties.getExecuteReviewableMin()) >= 0) {
            return ConfidenceBand.EXECUTE_AND_MARK_REVIEWABLE;
        }
        if (confidence.compareTo(properties.getNeedsReviewMin()) >= 0) {
            return ConfidenceBand.NEEDS_REVIEW;
        }
        return ConfidenceBand.DO_NOT_EXECUTE;
    }
}
