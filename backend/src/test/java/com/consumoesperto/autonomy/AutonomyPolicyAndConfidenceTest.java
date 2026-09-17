package com.consumoesperto.autonomy;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.config.FinancialAutonomyProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyPolicyAndConfidenceTest {

    @Test
    void faixasVemDasProperties() {
        FinancialAutonomyProperties props = new FinancialAutonomyProperties();
        ConfidenceEngine engine = new ConfidenceEngine(props);
        assertEquals(ConfidenceBand.AUTO_EXECUTE, engine.band(new BigDecimal("0.95")));
        assertEquals(ConfidenceBand.EXECUTE_AND_MARK_REVIEWABLE, engine.band(new BigDecimal("0.80")));
        assertEquals(ConfidenceBand.NEEDS_REVIEW, engine.band(new BigDecimal("0.60")));
        assertEquals(ConfidenceBand.DO_NOT_EXECUTE, engine.band(new BigDecimal("0.59")));
    }

    @Test
    void forbiddenNuncaExecuta() {
        AutonomyPolicyService policy = new AutonomyPolicyService();
        assertEquals(AutonomyActionClass.FORBIDDEN_AUTONOMOUS, policy.classify(AutonomyActionType.CREATE_PIX));
        assertFalse(policy.mayExecute(
            AutonomyActionClass.FORBIDDEN_AUTONOMOUS,
            AutonomyLevel.AUTONOMOUS_SAFE,
            ConfidenceBand.AUTO_EXECUTE));
    }

    @Test
    void manualNuncaExecuta() {
        AutonomyPolicyService policy = new AutonomyPolicyService();
        assertFalse(policy.mayExecute(
            AutonomyActionClass.SAFE_AUTO,
            AutonomyLevel.MANUAL,
            ConfidenceBand.AUTO_EXECUTE));
        assertEquals(PolicyOutcome.SUGGEST, policy.decide(
            AutonomyActionClass.SAFE_AUTO,
            AutonomyLevel.MANUAL,
            ConfidenceBand.AUTO_EXECUTE,
            true));
    }

    @Test
    void assistedSoSafeAutoComConfiancaMaxima() {
        AutonomyPolicyService policy = new AutonomyPolicyService();
        assertTrue(policy.mayExecute(
            AutonomyActionClass.SAFE_AUTO,
            AutonomyLevel.ASSISTED,
            ConfidenceBand.AUTO_EXECUTE));
        assertFalse(policy.mayExecute(
            AutonomyActionClass.AUTO_WITH_AUDIT,
            AutonomyLevel.ASSISTED,
            ConfidenceBand.AUTO_EXECUTE));
        assertEquals(PolicyOutcome.APPLY, policy.decide(
            AutonomyActionClass.SAFE_AUTO,
            AutonomyLevel.ASSISTED,
            ConfidenceBand.AUTO_EXECUTE,
            true));
        assertEquals(PolicyOutcome.APPLY, policy.decide(
            AutonomyActionClass.SAFE_AUTO,
            AutonomyLevel.AUTONOMOUS_SAFE,
            ConfidenceBand.AUTO_EXECUTE,
            true));
    }

    @Test
    void edithNaoParaSomaNemSaldo() {
        FinancialAutonomyProperties autonomy = new FinancialAutonomyProperties();
        autonomy.setEnabled(true);
        autonomy.setEdith(true);
        EdithProperties edith = new EdithProperties();
        edith.setEnabled(true);
        CognitiveDecisionPolicy policy = new CognitiveDecisionPolicy(autonomy, edith);
        assertFalse(policy.needsEdith(CognitiveDecisionPolicy.CognitiveNeed.SUM, false));
        assertFalse(policy.needsEdith(CognitiveDecisionPolicy.CognitiveNeed.BALANCE, false));
        assertFalse(policy.needsEdith(CognitiveDecisionPolicy.CognitiveNeed.KNOWN_RULE, false));
        assertTrue(policy.needsEdith(CognitiveDecisionPolicy.CognitiveNeed.UNKNOWN_CLASSIFICATION, false));
        assertFalse(policy.needsEdith(CognitiveDecisionPolicy.CognitiveNeed.UNKNOWN_CLASSIFICATION, true));
    }

    @Test
    void quietHoursOvernight() {
        assertTrue(QuietHours.active(
            java.time.LocalTime.of(23, 0),
            java.time.LocalTime.of(22, 0),
            java.time.LocalTime.of(7, 0)));
        assertTrue(QuietHours.active(
            java.time.LocalTime.of(6, 0),
            java.time.LocalTime.of(22, 0),
            java.time.LocalTime.of(7, 0)));
        assertFalse(QuietHours.active(
            java.time.LocalTime.of(12, 0),
            java.time.LocalTime.of(22, 0),
            java.time.LocalTime.of(7, 0)));
    }
}
