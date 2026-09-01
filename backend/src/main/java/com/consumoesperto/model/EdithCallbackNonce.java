package com.consumoesperto.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "edith_callback_nonce",
    uniqueConstraints = @UniqueConstraint(name = "ux_edith_callback_nonce", columnNames = "nonce")
)
public class EdithCallbackNonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nonce", nullable = false, length = 128)
    private String nonce;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected EdithCallbackNonce() {
    }

    public EdithCallbackNonce(String nonce, String requestId, LocalDateTime expiresAt) {
        this.nonce = nonce;
        this.requestId = requestId;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getNonce() {
        return nonce;
    }

    public String getRequestId() {
        return requestId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
