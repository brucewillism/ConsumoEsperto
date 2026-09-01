package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "edith_task_link",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_edith_task_link_client_request", columnNames = {"usuario_id", "client_request_id"}),
        @UniqueConstraint(name = "ux_edith_task_link_edith_task", columnNames = "edith_task_id")
    }
)
public class EdithTaskLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "context_ref", nullable = false, length = 64)
    private String contextRef;

    @Column(name = "edith_conversation_id", nullable = false, length = 128)
    private String edithConversationId;

    @Column(name = "edith_message_id", length = 128)
    private String edithMessageId;

    @Column(name = "edith_task_id", nullable = false, length = 128)
    private String edithTaskId;

    @Column(name = "edith_request_id", length = 128)
    private String edithRequestId;

    @Column(name = "client_request_id", nullable = false, length = 128)
    private String clientRequestId;

    @Column(name = "source_action", nullable = false, length = 64)
    private String sourceAction;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "QUEUED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected EdithTaskLink() {
    }

    public EdithTaskLink(
        Long usuarioId,
        String contextRef,
        String edithConversationId,
        String edithMessageId,
        String edithTaskId,
        String edithRequestId,
        String clientRequestId,
        String sourceAction
    ) {
        this.usuarioId = usuarioId;
        this.contextRef = contextRef;
        this.edithConversationId = edithConversationId;
        this.edithMessageId = edithMessageId;
        this.edithTaskId = edithTaskId;
        this.edithRequestId = edithRequestId;
        this.clientRequestId = clientRequestId;
        this.sourceAction = sourceAction;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getContextRef() {
        return contextRef;
    }

    public String getEdithConversationId() {
        return edithConversationId;
    }

    public String getEdithMessageId() {
        return edithMessageId;
    }

    public String getEdithTaskId() {
        return edithTaskId;
    }

    public String getEdithRequestId() {
        return edithRequestId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getSourceAction() {
        return sourceAction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
