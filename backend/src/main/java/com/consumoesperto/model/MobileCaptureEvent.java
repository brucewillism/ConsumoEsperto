package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_capture_events")
@Getter
@Setter
@NoArgsConstructor
public class MobileCaptureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private MobileCaptureDevice device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 40)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IngestionEventStatus status;

    @Column(name = "client_event_id", length = 128)
    private String clientEventId;

    @Column(name = "external_event_id", length = 128)
    private String externalEventId;

    @Column(length = 128)
    private String fingerprint;

    @Column(name = "parser_name", length = 80)
    private String parserName;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 8)
    private String currency;

    @Column(name = "merchant_raw", length = 300)
    private String merchantRaw;

    @Column(name = "merchant_normalized", length = 200)
    private String merchantNormalized;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Column(name = "notification_title", length = 300)
    private String notificationTitle;

    @Column(name = "notification_text", length = 1000)
    private String notificationText;

    @Column(name = "card_hint", length = 40)
    private String cardHint;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "transacao_id")
    private Long transacaoId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
