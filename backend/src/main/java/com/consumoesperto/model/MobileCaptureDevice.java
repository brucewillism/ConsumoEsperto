package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_capture_devices")
@Getter
@Setter
@NoArgsConstructor
public class MobileCaptureDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MobilePlatform platform;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_test_ok_at")
    private LocalDateTime lastTestOkAt;

    public boolean isUsable() {
        return active && revokedAt == null;
    }
}
