package com.consumoesperto.autonomy;

import com.consumoesperto.config.FinancialAutonomyProperties;
import com.consumoesperto.model.AutonomyPreferencia;
import com.consumoesperto.repository.AutonomyPreferenciaRepository;
import com.consumoesperto.repository.FinancialDomainEventRepository;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutonomyScheduler {

    private final FinancialAutonomyProperties properties;
    private final AutonomyJobLockService lockService;
    private final AutonomyPreferenciaRepository preferenciaRepository;
    private final FinancialDomainEventRepository eventRepository;
    private final FinancialAutonomyEngine engine;
    private final RecurringPatternDetectionService recurringPatternDetectionService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final AutonomyReviewService reviewService;
    private final AutonomyForecastService forecastService;
    private final SafeToSpendService safeToSpendService;
    private final LiveInvoiceProjectionService liveInvoiceProjectionService;
    private final JarvisProactiveOrchestrator jarvis;

    @Scheduled(initialDelay = 10_000, fixedDelay = 15_000)
    public void drenarEventosPendentes() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        if (!lockService.tryAcquire("autonomy-drain", Duration.ofSeconds(40))) {
            return;
        }
        eventRepository.findClaimable(
                AppTimeZone.agora(),
                AppTimeZone.agora().minusMinutes(Math.max(1, properties.getOutboxStaleProcessingMinutes())),
                org.springframework.data.domain.PageRequest.of(0, 50))
            .forEach(e -> {
                try {
                    engine.processEvent(e.getId());
                } catch (Exception ex) {
                    log.debug("autonomy_drain id={}: {}", e.getId(), ex.getMessage());
                }
            });
        try {
            jarvis.retryPendingDeliveries();
        } catch (Exception ex) {
            log.debug("jarvis_pending_retry: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 20 7 * * *", zone = "America/Sao_Paulo")
    public void recorrenciasEAnomalias() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        if (!lockService.tryAcquire("autonomy-patterns", Duration.ofMinutes(25))) {
            return;
        }
        for (AutonomyPreferencia pref : preferenciaRepository.findAll()) {
            try {
                if (pref.isDetectarAssinaturas()) {
                    recurringPatternDetectionService.detectarESugerir(pref.getUsuarioId());
                }
                if (pref.isDetectarAnomalias() && properties.isAnomaly()) {
                    for (var a : anomalyDetectionService.detectar(pref.getUsuarioId(), null)) {
                        reviewService.open(pref.getUsuarioId(), "ANOMALY", a.transacaoId(),
                            a.title(), a.detail(), a.confidence());
                    }
                }
            } catch (Exception e) {
                log.debug("autonomy_patterns userId={}: {}", pref.getUsuarioId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "America/Sao_Paulo")
    public void briefDiarioECashflow() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        if (!lockService.tryAcquire("autonomy-brief", Duration.ofMinutes(25))) {
            return;
        }
        for (AutonomyPreferencia pref : preferenciaRepository.findAll()) {
            Long usuarioId = pref.getUsuarioId();
            try {
                if (pref.isPreverSaldo()) {
                    Map<String, Object> fc = forecastService.horizontes(usuarioId);
                    if (Boolean.TRUE.equals(fc.get("cashflowRisco"))) {
                        reviewService.open(usuarioId, "CASHFLOW", null,
                            "Fluxo projetado negativo",
                            "A previsão de 30 dias indica saldo negativo.",
                            new BigDecimal("0.90"));
                        jarvis.notify(usuarioId, JarvisNotificationPriority.CRITICAL,
                            "Risco de caixa",
                            "Seu fluxo projetado fica negativo antes do próximo salário. Nada foi alterado automaticamente.");
                    }
                }
                if (pref.isResumoDiario()) {
                    Map<String, Object> safe = safeToSpendService.calcular(usuarioId);
                    List<Map<String, Object>> faturas = liveInvoiceProjectionService.atuais(usuarioId);
                    Object faturaVal = faturas.isEmpty() ? "—" : faturas.get(0).get("valorProjetado");
                    jarvis.notify(usuarioId, JarvisNotificationPriority.INFO,
                        "Resumo diário",
                        "Bom dia. Disponível hoje sem comprometer o mês: R$ "
                            + MoedaUtil.nz((BigDecimal) safe.get("safeToSpend"))
                            + ". Fatura: " + faturaVal
                            + ". Revisões: " + reviewService.countOpen(usuarioId) + ".");
                }
            } catch (Exception e) {
                log.debug("autonomy_brief userId={}: {}", usuarioId, e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 18 * * SUN", zone = "America/Sao_Paulo")
    public void reviewSemanal() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        if (!lockService.tryAcquire("autonomy-weekly", Duration.ofMinutes(25))) {
            return;
        }
        for (AutonomyPreferencia pref : preferenciaRepository.findAll()) {
            if (!pref.isResumoSemanal()) {
                continue;
            }
            try {
                long pending = reviewService.countOpen(pref.getUsuarioId());
                jarvis.notify(pref.getUsuarioId(), JarvisNotificationPriority.INFO,
                    "Revisão semanal",
                    pending == 0
                        ? "Semana encerrada sem itens pendentes de revisão."
                        : pending + " itens precisam de você nesta semana.");
            } catch (Exception e) {
                log.debug("autonomy_weekly userId={}: {}", pref.getUsuarioId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 15 9 1 * *", zone = "America/Sao_Paulo")
    public void closeMensal() {
        if (!properties.isEnabled() || !properties.isJobs()) {
            return;
        }
        if (!lockService.tryAcquire("autonomy-monthly", Duration.ofMinutes(40))) {
            return;
        }
        for (AutonomyPreferencia pref : preferenciaRepository.findAll()) {
            try {
                Map<String, Object> fc = forecastService.horizontes(pref.getUsuarioId());
                jarvis.notify(pref.getUsuarioId(), JarvisNotificationPriority.INFO,
                    "Fechamento do mês",
                    "Fechamento disponível. Previsão 30 dias: " + fc.get("d30"));
            } catch (Exception e) {
                log.debug("autonomy_monthly userId={}: {}", pref.getUsuarioId(), e.getMessage());
            }
        }
    }
}
