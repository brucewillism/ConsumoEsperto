package com.consumoesperto.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "edith_tool_audit")
public class EdithToolAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "tool_call_id", length = 128)
    private String toolCallId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "capability", nullable = false, length = 128)
    private String capability;

    @Column(name = "params_json", nullable = false, length = 2000)
    private String paramsJson;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected EdithToolAudit() {
    }

    public EdithToolAudit(
        String traceId,
        String toolCallId,
        Long userId,
        String capability,
        String paramsJson,
        int rowCount,
        long durationMs,
        String outcome
    ) {
        this.traceId = traceId;
        this.toolCallId = toolCallId;
        this.userId = userId;
        this.capability = capability;
        this.paramsJson = paramsJson;
        this.rowCount = rowCount;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCapability() {
        return capability;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public int getRowCount() {
        return rowCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getOutcome() {
        return outcome;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
