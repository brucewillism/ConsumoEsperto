package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "autonomy_decision_log")
@Getter
@Setter
@NoArgsConstructor
public class AutonomyDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "decision_type", nullable = false, length = 48)
    private String decisionType;

    @Column(name = "policy", nullable = false, length = 32)
    private String policy;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "rule_code", length = 80)
    private String ruleCode;

    @Column(name = "reason", length = 400)
    private String reason;

    @Column(name = "cognitive_used", nullable = false)
    private boolean cognitiveUsed;

    @Column(name = "edith_task_id", length = 80)
    private String edithTaskId;

    @Column(name = "transacao_id")
    private Long transacaoId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
