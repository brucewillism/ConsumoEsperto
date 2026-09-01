package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "edith_conversation_link",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_edith_conversation_link_edith_id",
        columnNames = "edith_conversation_id"
    )
)
public class EdithConversationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "edith_conversation_id", nullable = false, length = 128)
    private String edithConversationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected EdithConversationLink() {
    }

    public EdithConversationLink(Long usuarioId, String edithConversationId) {
        this.usuarioId = usuarioId;
        this.edithConversationId = edithConversationId;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getEdithConversationId() {
        return edithConversationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
