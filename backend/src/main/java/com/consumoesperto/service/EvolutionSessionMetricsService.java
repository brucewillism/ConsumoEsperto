package com.consumoesperto.service;

import com.consumoesperto.util.AppTimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas in-memory por instância Evolution — alimentam health admin e logs de instabilidade.
 */
@Service
@Slf4j
public class EvolutionSessionMetricsService {

    private static final int BASE_MEMORIA_MB = 48;
    private static final int MEMORIA_POR_1K_MSG_MB = 4;
    private static final int MEMORIA_MAX_MB = 256;

    private final ConcurrentHashMap<String, InstanceMetrics> porInstancia = new ConcurrentHashMap<>();
    private volatile LocalDate diaContadores = AppTimeZone.hoje();

    @Value("${consumoesperto.evolution.metrics.instabilidade.desconexoes-dia:3}")
    private int limiteDesconexoesInstavel;

    @Value("${consumoesperto.evolution.metrics.instabilidade.reconexoes-dia:5}")
    private int limiteReconexoesInstavel;

    @Value("${consumoesperto.evolution.metrics.instabilidade.inatividade-min:45}")
    private int inatividadeInstavelMinutos;

    public void recordIncoming(String instanceName) {
        metricsOf(instanceName).mensagensRecebidasTotal.incrementAndGet();
        metricsOf(instanceName).mensagensRecebidasHoje.incrementAndGet();
        touchActivity(instanceName);
    }

    public void recordOutgoing(String instanceName) {
        metricsOf(instanceName).mensagensEnviadasTotal.incrementAndGet();
        metricsOf(instanceName).mensagensEnviadasHoje.incrementAndGet();
        touchActivity(instanceName);
    }

    public void recordDisconnect(String instanceName, String state) {
        resetDailyIfNeeded();
        InstanceMetrics m = metricsOf(instanceName);
        m.desconexoesTotal.incrementAndGet();
        m.desconexoesHoje.incrementAndGet();
        m.connectedSinceMs = 0L;
        m.lastDisconnectAtMs = System.currentTimeMillis();
        logStructured("evolution.session.disconnected", instanceName, Map.of(
            "state", state != null ? state : "unknown",
            "desconexoesHoje", m.desconexoesHoje.get()
        ));
    }

    public void recordReconnect(String instanceName, String reason) {
        resetDailyIfNeeded();
        InstanceMetrics m = metricsOf(instanceName);
        m.reconexoesTotal.incrementAndGet();
        m.reconexoesHoje.incrementAndGet();
        m.connectedSinceMs = System.currentTimeMillis();
        touchActivity(instanceName);
        logStructured("evolution.session.reconnected", instanceName, Map.of(
            "reason", reason != null ? reason : "unknown",
            "reconexoesHoje", m.reconexoesHoje.get()
        ));
    }

    public void recordConnected(String instanceName) {
        InstanceMetrics m = metricsOf(instanceName);
        if (m.connectedSinceMs <= 0L) {
            m.connectedSinceMs = System.currentTimeMillis();
        }
        touchActivity(instanceName);
    }

    public void recordSendFailure(String instanceName) {
        resetDailyIfNeeded();
        InstanceMetrics m = metricsOf(instanceName);
        m.falhasHoje.incrementAndGet();
        m.sendFailuresRecent.incrementAndGet();
        touchActivity(instanceName);
    }

    public void recordApiLatency(String instanceName, long durationMs) {
        if (durationMs < 0) {
            return;
        }
        metricsOf(instanceName).recordLatency(durationMs);
    }

    public void recordFetchFailure() {
        resetDailyIfNeeded();
        metricsOf("_global_").falhasHoje.incrementAndGet();
    }

    public int totalFalhasHoje() {
        resetDailyIfNeeded();
        return porInstancia.values().stream().mapToInt(m -> m.falhasHoje.get()).sum();
    }

    public long latenciaMediaGlobalMs() {
        return aggregateLatencyStats().mediaMs();
    }

    public long latenciaP95GlobalMs() {
        return aggregateLatencyStats().p95Ms();
    }

    public long totalMensagensHoje() {
        resetDailyIfNeeded();
        return porInstancia.values().stream()
            .mapToLong(m -> m.mensagensEnviadasHoje.get() + m.mensagensRecebidasHoje.get())
            .sum();
    }

    public int totalReconexoesHoje() {
        resetDailyIfNeeded();
        return porInstancia.values().stream().mapToInt(m -> m.reconexoesHoje.get()).sum();
    }

    public InstanceSnapshot snapshot(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            return emptySnapshot("");
        }
        resetDailyIfNeeded();
        InstanceMetrics m = metricsOf(instanceName);
        long now = System.currentTimeMillis();
        long uptimeSec = m.connectedSinceMs > 0 ? Math.max(0, (now - m.connectedSinceMs) / 1000) : 0;
        long inactivitySec = m.lastActivityAtMs > 0 ? Math.max(0, (now - m.lastActivityAtMs) / 1000) : -1;
        long totalMsg = m.mensagensEnviadasTotal.get() + m.mensagensRecebidasTotal.get();
        int memMb = estimateMemoryMb(totalMsg);
        Instability inst = evaluateInstability(instanceName, m, inactivitySec);
        return new InstanceSnapshot(
            instanceName.trim(),
            uptimeSec,
            memMb,
            m.mensagensEnviadasTotal.get(),
            m.mensagensRecebidasTotal.get(),
            m.mensagensEnviadasHoje.get(),
            m.mensagensRecebidasHoje.get(),
            m.desconexoesHoje.get(),
            m.reconexoesHoje.get(),
            m.falhasHoje.get(),
            m.latenciaMediaMs(),
            m.latenciaP95Ms(),
            inactivitySec,
            inst.instavel(),
            inst.motivo()
        );
    }

    @Scheduled(fixedDelayString = "${consumoesperto.evolution.metrics.instabilidade.scan-ms:300000}")
    public void scanAndLogSessoesInstaveis() {
        List<String> instaveis = new ArrayList<>();
        for (String instance : porInstancia.keySet()) {
            InstanceSnapshot snap = snapshot(instance);
            if (snap.instavel()) {
                instaveis.add(instance);
                logStructured("evolution.session.unstable", instance, Map.of(
                    "motivo", snap.motivoInstabilidade(),
                    "desconexoesHoje", snap.desconexoesHoje(),
                    "reconexoesHoje", snap.reconexoesHoje(),
                    "idadeUltimaAtividadeSegundos", snap.idadeUltimaAtividadeSegundos(),
                    "uptimeSegundos", snap.uptimeSegundos(),
                    "memoriaEstimadaMb", snap.memoriaEstimadaMb()
                ));
            }
        }
        if (!instaveis.isEmpty()) {
            log.warn("[EvolutionMetrics] Sessões instáveis detectadas: {}", instaveis);
        }
    }

    private Instability evaluateInstability(String instance, InstanceMetrics m, long inactivitySec) {
        List<String> motivos = new ArrayList<>();
        if (m.desconexoesHoje.get() >= limiteDesconexoesInstavel) {
            motivos.add("desconexoes_hoje=" + m.desconexoesHoje.get());
        }
        if (m.reconexoesHoje.get() >= limiteReconexoesInstavel) {
            motivos.add("reconexoes_hoje=" + m.reconexoesHoje.get());
        }
        if (m.sendFailuresRecent.get() >= 3) {
            motivos.add("falhas_envio_recentes=" + m.sendFailuresRecent.get());
        }
        if (m.connectedSinceMs > 0 && inactivitySec >= inatividadeInstavelMinutos * 60L) {
            motivos.add("inatividade_" + inactivitySec + "s");
        }
        if (motivos.isEmpty()) {
            return new Instability(false, null);
        }
        return new Instability(true, String.join(", ", motivos));
    }

    private void touchActivity(String instanceName) {
        metricsOf(instanceName).lastActivityAtMs = System.currentTimeMillis();
    }

    private InstanceMetrics metricsOf(String instanceName) {
        resetDailyIfNeeded();
        String key = instanceName.trim();
        return porInstancia.computeIfAbsent(key, k -> new InstanceMetrics());
    }

    private void resetDailyIfNeeded() {
        LocalDate hoje = AppTimeZone.hoje();
        if (hoje.equals(diaContadores)) {
            return;
        }
        synchronized (this) {
            if (hoje.equals(diaContadores)) {
                return;
            }
            for (InstanceMetrics m : porInstancia.values()) {
                m.mensagensEnviadasHoje.set(0);
                m.mensagensRecebidasHoje.set(0);
                m.desconexoesHoje.set(0);
                m.reconexoesHoje.set(0);
                m.falhasHoje.set(0);
                m.sendFailuresRecent.set(0);
            }
            diaContadores = hoje;
            log.info("[EvolutionMetrics] Contadores diários reiniciados para {}", hoje);
        }
    }

    private static int estimateMemoryMb(long totalMessages) {
        int extra = (int) Math.min(120, (totalMessages / 1000L) * MEMORIA_POR_1K_MSG_MB);
        return Math.min(MEMORIA_MAX_MB, BASE_MEMORIA_MB + extra);
    }

    private static InstanceSnapshot emptySnapshot(String instance) {
        return new InstanceSnapshot(instance, 0, BASE_MEMORIA_MB, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, false, null);
    }

    private LatencyStats aggregateLatencyStats() {
        List<Long> all = new ArrayList<>();
        for (InstanceMetrics m : porInstancia.values()) {
            all.addAll(m.latencySamplesCopy());
        }
        return LatencyStats.from(all);
    }

    private void logStructured(String event, String instance, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("instance", instance);
        payload.put("timestamp", Instant.now().toString());
        payload.putAll(fields);
        log.info("[EvolutionMetrics] {}", payload);
    }

    public record InstanceSnapshot(
        String instancia,
        long uptimeSegundos,
        int memoriaEstimadaMb,
        long mensagensEnviadas,
        long mensagensRecebidas,
        long mensagensEnviadasHoje,
        long mensagensRecebidasHoje,
        int desconexoesHoje,
        int reconexoesHoje,
        int falhasHoje,
        long latenciaMediaMs,
        long latenciaP95Ms,
        long idadeUltimaAtividadeSegundos,
        boolean instavel,
        String motivoInstabilidade
    ) {}

    private record LatencyStats(long mediaMs, long p95Ms) {
        static LatencyStats from(List<Long> samples) {
            if (samples == null || samples.isEmpty()) {
                return new LatencyStats(0, 0);
            }
            List<Long> sorted = new ArrayList<>(samples);
            sorted.sort(Long::compareTo);
            long sum = 0;
            for (Long v : sorted) {
                sum += v;
            }
            long media = sum / sorted.size();
            int idx = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1);
            return new LatencyStats(media, sorted.get(Math.max(0, idx)));
        }
    }

    private record Instability(boolean instavel, String motivo) {}

    private static final class InstanceMetrics {
        private final AtomicLong mensagensEnviadasTotal = new AtomicLong();
        private final AtomicLong mensagensRecebidasTotal = new AtomicLong();
        private final AtomicLong mensagensEnviadasHoje = new AtomicLong();
        private final AtomicLong mensagensRecebidasHoje = new AtomicLong();
        private final AtomicInteger desconexoesTotal = new AtomicInteger();
        private final AtomicInteger reconexoesTotal = new AtomicInteger();
        private final AtomicInteger desconexoesHoje = new AtomicInteger();
        private final AtomicInteger reconexoesHoje = new AtomicInteger();
        private final AtomicInteger falhasHoje = new AtomicInteger();
        private final AtomicInteger sendFailuresRecent = new AtomicInteger();
        private final ArrayDeque<Long> latenciaMs = new ArrayDeque<>();
        private volatile long connectedSinceMs;
        private volatile long lastDisconnectAtMs;
        private volatile long lastActivityAtMs;

        private void recordLatency(long durationMs) {
            synchronized (latenciaMs) {
                latenciaMs.addLast(durationMs);
                while (latenciaMs.size() > 200) {
                    latenciaMs.pollFirst();
                }
            }
        }

        private long latenciaMediaMs() {
            return LatencyStats.from(latencySamplesCopy()).mediaMs();
        }

        private long latenciaP95Ms() {
            return LatencyStats.from(latencySamplesCopy()).p95Ms();
        }

        private List<Long> latencySamplesCopy() {
            synchronized (latenciaMs) {
                return new ArrayList<>(latenciaMs);
            }
        }
    }

    public static boolean isConnectedStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        return "open".equals(s) || "connected".equals(s) || "online".equals(s);
    }
}
