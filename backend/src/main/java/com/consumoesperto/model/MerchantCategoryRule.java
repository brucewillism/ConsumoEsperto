package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_category_rules")
@Getter
@Setter
@NoArgsConstructor
public class MerchantCategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "merchant_pattern", nullable = false, length = 200)
    private String merchantPattern;

    @Column(name = "merchant_normalized", length = 200)
    private String merchantNormalized;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(nullable = false, length = 40)
    private String origin = "USER";

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
