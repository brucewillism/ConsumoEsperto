package com.consumoesperto.service;

import com.consumoesperto.config.WhatsappConexaoMonitorProperties;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deteta sessões Evolution «fantasma» (UI open, Baileys morto) e tenta recuperar com restart + webhook.
 * Também regista actividade recente por instância para diagnóstico.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvolutionSessionWatchdogService {

    private static final long RECOVER_DEBOUNCE_MS = 180_000L;

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final EvolutionPairingService evolutionPairingService;
    private final EvolutionWaSessionRegistry evolutionWaSessionRegistry;
    private final EvolutionInstanceSettingsService evolutionInstanceSettingsService;
    private final EvolutionInstanceLifecycleService evolutionInstanceLifecycleService;
    private final EvolutionSessionMetricsService evolutionSessionMetricsService;
    private final WhatsappConexaoMonitorService whatsappConexaoMonitorService;
    private final WhatsappConexaoMonitorProperties whatsappConexaoMonitorProperties;

    private final ConcurrentHashMap<String, Long> lastWebhookActivityMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastRecoverAttemptMs = new ConcurrentHashMap<>();

    @Value("${consumoesperto.evolution.watchdog.enabled:true}")
    private boolean watchdogEnabled;

    public void touchWebhookActivity(String evolutionInstanceName) {
        if (evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return;
        }
        lastWebhookActivityMs.put(evolutionInstanceName.trim(), System.currentTimeMillis());
    }

    /**
     * Chamado quando a Evolution reporta perda de ligação ({@code connection.update} close/disconnected).
     */
    public void onConnectionLost(String evolutionInstanceName, String state) {
        if (!watchdogEnabled || evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return;
        }
        log.warn("Evolution CONNECTION_UPDATE perda de sessão: instance={} state={}", evolutionInstanceName, state);
        if (whatsappConexaoMonitorProperties.isEnabled()) {
            whatsappConexaoMonitorService.aoEventoConexao(evolutionInstanceName.trim(), state);
        } else {
            attemptRecoverInstance(evolutionInstanceName.trim(), "connection-update:" + state);
        }
    }

    /**
     * Após falha ao enviar mensagem, tenta recuperar a instância (debounced).
     */
    public void onSendFailure(String evolutionInstanceName) {
        if (!watchdogEnabled || evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return;
        }
        if (whatsappConexaoMonitorProperties.isEnabled()) {
            whatsappConexaoMonitorService.solicitarReconexaoPorInstancia(
                evolutionInstanceName.trim(), "send-failure");
        } else {
            attemptRecoverInstance(evolutionInstanceName.trim(), "send-failure");
        }
    }

    /**
     * Poll de sessões fantasma. A sondagem periódica e o backoff passam a
     * {@link WhatsappConexaoMonitorService} (cron 5 min). Este método fica para
     * chamadas manuais/diagnóstico.
     */
    @Transactional(readOnly = true)
    public void scanStaleSessions() {
        if (!watchdogEnabled) {
            return;
        }
        whatsappConexaoMonitorService.verificarTodasInstanciasVinculadas();
    }

    /**
     * Keepalive periódico: sessões abertas mantêm webhook, settings e presença unavailable
     * sem restart destrutivo — evita Jarvis «morto» após dias de inactividade.
     */
    @Scheduled(fixedDelayString = "${consumoesperto.evolution.keepalive.interval-ms:14400000}")
    public void sessionKeepalive() {
        if (!watchdogEnabled) {
            return;
        }
        for (UsuarioAiConfig cfg : usuarioAiConfigRepository.findAll()) {
            if (cfg == null || cfg.getUsuario() == null || cfg.getUsuario().getId() == null) {
                continue;
            }
            Long userId = cfg.getUsuario().getId();
            if (evolutionWaSessionRegistry.isUserDisconnected(userId)) {
                continue;
            }
            String instance = cfg.getEvolutionInstanceName();
            if (instance == null || instance.isBlank()) {
                continue;
            }
            String name = instance.trim();
            evolutionPairingService.invalidatePairingCredCache(userId);
            EvolutionPairingService.ResolvedEvolutionCred cred = evolutionPairingService.resolveCredentials(userId);
            if (cred == null || cred.instanceName == null || cred.instanceName.isBlank()) {
                continue;
            }
            if (evolutionPairingService.isGhostOpenStaleInstance(cred)) {
                recoverOrDelegate(name, "keepalive-ghost");
                continue;
            }
            if (!evolutionPairingService.isRealWaSessionOpen(cred)) {
                recoverOrDelegate(name, "keepalive-not-open");
                continue;
            }
            try {
                evolutionInstanceLifecycleService.ensureInstanceWebhook(name);
                evolutionPairingService.attemptSessionReconnect(name);
                evolutionInstanceSettingsService.maintainPhoneFriendlySession(name);
            } catch (Exception e) {
                log.debug("Evolution keepalive [{}]: {}", name, e.getMessage());
            }
        }
    }

    private void recoverOrDelegate(String instanceName, String reason) {
        if (whatsappConexaoMonitorProperties.isEnabled()) {
            whatsappConexaoMonitorService.solicitarReconexaoPorInstancia(instanceName, reason);
        } else {
            attemptRecoverInstance(instanceName, reason);
        }
    }

    private void attemptRecoverInstance(String instanceName, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastRecoverAttemptMs.get(instanceName);
        if (last != null && now - last < RECOVER_DEBOUNCE_MS) {
            log.debug("Evolution watchdog: recuperação debounced instance={} reason={}", instanceName, reason);
            return;
        }
        lastRecoverAttemptMs.put(instanceName, now);
        log.warn("Evolution watchdog: a recuperar instância {} (motivo={})", instanceName, reason);
        try {
            evolutionInstanceLifecycleService.ensureInstanceWebhook(instanceName);
            if (evolutionPairingService.attemptSessionReconnect(instanceName)) {
                log.info("Evolution watchdog: instance={} reconectada sem restart (motivo={})", instanceName, reason);
                evolutionSessionMetricsService.recordReconnect(instanceName, reason);
                evolutionInstanceSettingsService.maintainPhoneFriendlySession(instanceName);
                evolutionInstanceSettingsService.markInstanceStabilized(instanceName);
                return;
            }
            boolean restarted = evolutionInstanceSettingsService.restartInstance(instanceName);
            log.info("Evolution watchdog: instance={} restart={} reason={}", instanceName, restarted, reason);
            if (restarted && evolutionPairingService.attemptSessionReconnect(instanceName)) {
                evolutionSessionMetricsService.recordReconnect(instanceName, "watchdog-restart:" + reason);
                evolutionInstanceSettingsService.maintainPhoneFriendlySession(instanceName);
                evolutionInstanceSettingsService.markInstanceStabilized(instanceName);
            }
        } catch (Exception e) {
            log.warn("Evolution watchdog: falha ao recuperar {}: {}", instanceName, e.getMessage());
        }
    }

    /** Útil para diagnóstico em logs/admin. */
    public long lastWebhookActivityAgeMs(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            return -1L;
        }
        Long ts = lastWebhookActivityMs.get(instanceName.trim());
        if (ts == null) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - ts);
    }

    public static boolean isConnectionLostState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        return "close".equals(s)
            || "closed".equals(s)
            || "disconnected".equals(s)
            || "disconnect".equals(s)
            || "logout".equals(s)
            || "refused".equals(s);
    }
}
