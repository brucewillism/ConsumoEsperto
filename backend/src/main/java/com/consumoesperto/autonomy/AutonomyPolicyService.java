package com.consumoesperto.autonomy;

import org.springframework.stereotype.Component;

@Component
public class AutonomyPolicyService {

    public AutonomyActionClass classify(AutonomyActionType type) {
        if (type == null) {
            return AutonomyActionClass.FORBIDDEN_AUTONOMOUS;
        }
        return switch (type) {
            case CATEGORIZE_KNOWN_MERCHANT, RECONCILE_DUPLICATE, CONFIRM_OFFICIAL_SOURCE,
                 UPDATE_FORECAST, DETECT_RECURRENCE, RECALCULATE_INVOICE, GENERATE_ALERT
                -> AutonomyActionClass.SAFE_AUTO;
            case REGISTER_TRUSTED_TRANSACTION, SUGGEST_SUBSCRIPTION, UPDATE_MERCHANT_RULE_AFTER_CONFIRM
                -> AutonomyActionClass.AUTO_WITH_AUDIT;
            case CREATE_PERMANENT_RECURRENCE, CHANGE_BUDGET, CHANGE_GOAL,
                 DELETE_TRANSACTION, CANCEL_SUBSCRIPTION
                -> AutonomyActionClass.REQUIRE_CONFIRMATION;
            case TRANSFER_MONEY, PAY_INVOICE, CREATE_PIX, MOVE_BALANCE,
                 CHANGE_CREDENTIALS, DELETE_HISTORY, EXTERNAL_BANK_ACTION
                -> AutonomyActionClass.FORBIDDEN_AUTONOMOUS;
        };
    }

    public boolean mayExecute(AutonomyActionClass actionClass, AutonomyLevel level, ConfidenceBand band) {
        if (actionClass == AutonomyActionClass.FORBIDDEN_AUTONOMOUS) {
            return false;
        }
        if (actionClass == AutonomyActionClass.REQUIRE_CONFIRMATION) {
            return false;
        }
        if (level == AutonomyLevel.MANUAL) {
            return false;
        }
        if (band == ConfidenceBand.DO_NOT_EXECUTE || band == ConfidenceBand.NEEDS_REVIEW) {
            return false;
        }
        if (level == AutonomyLevel.ASSISTED) {
            return actionClass == AutonomyActionClass.SAFE_AUTO && band == ConfidenceBand.AUTO_EXECUTE;
        }
        return actionClass == AutonomyActionClass.SAFE_AUTO
            || actionClass == AutonomyActionClass.AUTO_WITH_AUDIT;
    }

    /**
     * APPLY só depois da policy. MANUAL nunca aplica; pode SUGGEST.
     */
    public PolicyOutcome decide(
        AutonomyActionClass actionClass,
        AutonomyLevel level,
        ConfidenceBand band,
        boolean classificarAuto
    ) {
        if (actionClass == AutonomyActionClass.FORBIDDEN_AUTONOMOUS) {
            return PolicyOutcome.FORBIDDEN;
        }
        if (actionClass == AutonomyActionClass.REQUIRE_CONFIRMATION) {
            return PolicyOutcome.REVIEW;
        }
        if (classificarAuto && mayExecute(actionClass, level, band)) {
            return PolicyOutcome.APPLY;
        }
        if (band == ConfidenceBand.AUTO_EXECUTE || band == ConfidenceBand.EXECUTE_AND_MARK_REVIEWABLE) {
            return PolicyOutcome.SUGGEST;
        }
        return PolicyOutcome.REVIEW;
    }
}
