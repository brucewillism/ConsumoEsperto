package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_source_mappings")
@Getter
@Setter
@NoArgsConstructor
public class MobileSourceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private MobileCaptureDevice device;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Column(name = "provider_key", length = 80)
    private String providerKey;

    @Column(name = "card_last4", length = 8)
    private String cardLast4;

    @Column(name = "conta_id")
    private Long contaId;

    @Column(name = "cartao_id")
    private Long cartaoId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
