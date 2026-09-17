package com.consumoesperto.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "consumoesperto.autonomy")
public class FinancialAutonomyProperties {

    /** Master flag — default conservador. */
    private boolean enabled = false;
    private boolean proactiveJarvis = false;
    private boolean edith = false;
    private boolean merchantLearning = true;
    private boolean reconciliation = true;
    private boolean anomaly = true;
    private boolean jobs = true;

    /** Margem de segurança do safe-to-spend (0.10 = 10%). */
    private BigDecimal safetyMargin = new BigDecimal("0.10");

    private BigDecimal autoExecuteMin = new BigDecimal("0.95");
    private BigDecimal executeReviewableMin = new BigDecimal("0.80");
    private BigDecimal needsReviewMin = new BigDecimal("0.60");

    private int duplicateWindowMinutes = 15;
    private String silenciosoInicioDefault = "22:00";
    private String silenciosoFimDefault = "07:00";

    /** Backoff do outbox (segundos): 30s, 2min, 10min, 30min. */
    private List<Integer> outboxRetrySeconds = new ArrayList<>(List.of(30, 120, 600, 1800));
    private int outboxMaxAttempts = 4;
    private double outboxJitterRatio = 0.20;
    /** Reclaim de PROCESSING preso (minuto). */
    private int outboxStaleProcessingMinutes = 3;

    /** Teto de espera da classificação via E.D.I.T.H. — não bloqueia o outbox por minutos. */
    private long edithClassifyTimeoutMs = 20_000L;
}
