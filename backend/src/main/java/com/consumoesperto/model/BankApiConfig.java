package com.consumoesperto.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Modelo para configurações das APIs bancárias
 * Permite configurar dinamicamente as credenciais de cada banco por usuário
 */
@Entity
@Table(name = "bank_api_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankApiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_code", nullable = false)
    private String bankCode; // ITAU, MERCADOPAGO, NUBANK, INTER

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "api_url", nullable = false)
    private String apiUrl;

    @Column(name = "auth_url")
    private String authUrl;

    @Column(name = "token_url")
    private String tokenUrl;

    @Column(name = "redirect_uri")
    private String redirectUri;

    @Column(name = "scope")
    private String scope;

    @Column(name = "is_sandbox")
    private Boolean isSandbox = true;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "timeout_ms")
    private Integer timeoutMs = 30000;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "retry_delay_ms")
    private Integer retryDelayMs = 1000;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    @Column(name = "last_test_status")
    private String lastTestStatus; // SUCCESS, FAILED, NOT_TESTED

    @Column(name = "last_test_message")
    private String lastTestMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastTestStatus = "NOT_TESTED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
