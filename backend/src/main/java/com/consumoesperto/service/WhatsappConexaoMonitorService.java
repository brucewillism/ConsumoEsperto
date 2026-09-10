package com.consumoesperto.service;

import com.consumoesperto.config.WhatsappConexaoMonitorProperties;
import com.consumoesperto.dto.WhatsappConexaoStatusDTO;
import com.consumoesperto.dto.WhatsappConexaoTransicaoDTO;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.model.WhatsappConexaoStatus;
import com.consumoesperto.model.WhatsappConexaoTransicao;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.WhatsappConexaoStatusRepository;
import com.consumoesperto.repository.WhatsappConexaoTransicaoRepository;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.LogSanitizer;
import com.consumoesperto.whatsapp.WhatsappConexaoEstados;
import com.consumoesperto.whatsapp.WhatsappReconexaoBackoff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sonda o estado da instância Evolution, persiste transições, reconecta com backoff
 * (nunca apaga/recria a instância) e dispara alerta operacional quando a sessão
 * não volta sozinha.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappConexaoMonitorService {

    private final WhatsappConexaoMonitorProperties properties;
    private final WhatsappConexaoStatusRepository statusRepository;
    private final WhatsappConexaoTransicaoRepository transicaoRepository;
    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final EvolutionPairingService evolutionPairingService;
    private final EvolutionInstanceSettingsService evolutionInstanceSettingsService;
    private final EvolutionInstanceLifecycleService evolutionInstanceLifecycleService;
    private final EvolutionSessionMetricsService evolutionSessionMetricsService;
    private final EvolutionWaSessionRegistry evolutionWaSessionRegistry;
    private final AlertaOperacionalService alertaOperacionalService;

    @Value("${app.frontend-base-url:http://localhost:14200}")
    private String frontendBaseUrl;

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    @Scheduled(
        cron = "${consumoesperto.whatsapp.monitor.cron:0 */5 * * * *}",
        zone = "America/Sao_Paulo"
    )
    public void verificarTodasInstanciasVinculadas() {
        if (!properties.isEnabled()) {
            return;
        }
        for (UsuarioAiConfig cfg : usuarioAiConfigRepository.findAll()) {
            if (cfg == null || cfg.getUsuario() == null || cfg.getUsuario().getId() == null) {
                continue;
            }
            try {
                verificarUsuario(cfg.getUsuario().getId(), "poll");
            } catch (Exception e) {
                log.warn("Monitor WhatsApp: falha ao verificar userId={}: {}",
                    cfg.getUsuario().getId(), LogSanitizer.sanitize(e.getMessage()));
            }
        }
    }

    /**
     * Evento {@code CONNECTION_UPDATE} da Evolution — caminho rápido (segundos, não minutos).
     */
    public void aoEventoConexao(String evolutionInstanceName, String stateRaw) {
        if (!properties.isEnabled() || evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return;
        }
        Optional<UsuarioAiConfig> cfg =
            usuarioAiConfigRepository.findByEvolutionInstanceNameIgnoreCase(evolutionInstanceName.trim());
        if (cfg.isEmpty() || cfg.get().getUsuario() == null || cfg.get().getUsuario().getId() == null) {
            log.debug("Monitor WhatsApp: CONNECTION_UPDATE sem utilizador mapeado instance={}",
                evolutionInstanceName);
            return;
        }
        Long userId = cfg.get().getUsuario().getId();
        String instance = evolutionInstanceName.trim();
        String estado = WhatsappConexaoEstados.normalizar(stateRaw);
        String detalhe = "webhook:" + (stateRaw == null ? "" : stateRaw.trim());
        synchronized (lockFor(userId)) {
            if (WhatsappConexaoEstados.OPEN.equals(estado)) {
                // Confirma na API (open fantasma da Evolution não deve limpar alerta/backoff).
                verificarUsuarioLocked(userId, "webhook");
                return;
            }
            persistirEstado(userId, instance, estado, detalhe, "webhook");
            if (WhatsappConexaoEstados.isQueda(estado)
                && !evolutionWaSessionRegistry.isUserDisconnected(userId)) {
                tentarReconectar(userId, instance, false);
            }
        }
    }

    public void solicitarReconexaoPorInstancia(String evolutionInstanceName, String motivo) {
        if (!properties.isEnabled() || evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return;
        }
        usuarioAiConfigRepository.findByEvolutionInstanceNameIgnoreCase(evolutionInstanceName.trim())
            .map(UsuarioAiConfig::getUsuario)
            .filter(u -> u != null && u.getId() != null)
            .ifPresent(u -> {
                synchronized (lockFor(u.getId())) {
                    verificarUsuarioLocked(u.getId(), motivo == null ? "recover" : motivo);
                }
            });
    }

    public void verificarUsuario(Long usuarioId, String origem) {
        if (!properties.isEnabled() || usuarioId == null) {
            return;
        }
        synchronized (lockFor(usuarioId)) {
            verificarUsuarioLocked(usuarioId, origem == null ? "poll" : origem);
        }
    }

    @Transactional(readOnly = true)
    public WhatsappConexaoStatusDTO obterStatus(Long usuarioId) {
        if (usuarioId == null) {
            return WhatsappConexaoStatusDTO.builder()
                .estado(WhatsappConexaoEstados.MISSING)
                .detalhe("utilizador ausente")
                .transicoes(List.of())
                .build();
        }
        Optional<WhatsappConexaoStatus> snap = statusRepository.findById(usuarioId);
        List<WhatsappConexaoTransicaoDTO> hist = new ArrayList<>();
        for (WhatsappConexaoTransicao t : transicaoRepository.findTop20ByUsuarioIdOrderByVerificadoEmDesc(usuarioId)) {
            hist.add(WhatsappConexaoTransicaoDTO.builder()
                .estadoAnterior(t.getEstadoAnterior())
                .estadoNovo(t.getEstadoNovo())
                .detalhe(t.getDetalhe())
                .origem(t.getOrigem())
                .verificadoEm(t.getVerificadoEm())
                .build());
        }
        if (snap.isEmpty()) {
            return WhatsappConexaoStatusDTO.builder()
                .estado("unknown")
                .detalhe("ainda sem sondagem")
                .transicoes(hist)
                .build();
        }
        WhatsappConexaoStatus s = snap.get();
        LocalDateTime agora = AppTimeZone.agora();
        long minutos = s.getEstadoDesde() == null
            ? 0L
            : Math.max(0L, Duration.between(s.getEstadoDesde(), agora).toMinutes());
        boolean esgotada = s.getTentativasReconexao() >= Math.max(1, properties.getMaxTentativas())
            && WhatsappConexaoEstados.isQueda(s.getEstado());
        return WhatsappConexaoStatusDTO.builder()
            .estado(s.getEstado())
            .detalhe(s.getDetalhe())
            .instanceName(s.getInstanceName())
            .estadoDesde(s.getEstadoDesde())
            .verificadoEm(s.getVerificadoEm())
            .minutosNoEstado(minutos)
            .tentativasReconexao(s.getTentativasReconexao())
            .proximaTentativaEm(s.getProximaTentativaEm())
            .ultimaTentativaEm(s.getUltimaTentativaEm())
            .ultimoResultadoReconexao(s.getUltimoResultadoReconexao())
            .autoReconexaoEsgotada(esgotada)
            .alertaDesconexaoEnviado(s.isAlertaDesconexaoEnviado())
            .transicoes(hist)
            .build();
    }

    /** Botão «reconectar agora»: ignora backoff e teto (ainda sem delete/recreate). */
    public WhatsappConexaoStatusDTO reconectarAgora(Long usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Utilizador não autenticado");
        }
        synchronized (lockFor(usuarioId)) {
            String instance = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
            WhatsappConexaoStatus cur = statusRepository.findById(usuarioId).orElse(null);
            if (cur != null) {
                cur.setTentativasReconexao(0);
                cur.setProximaTentativaEm(null);
                statusRepository.save(cur);
            }
            if (instance != null && !instance.isBlank()) {
                tentarReconectar(usuarioId, instance.trim(), true);
            }
            verificarUsuarioLocked(usuarioId, "manual");
        }
        return obterStatus(usuarioId);
    }

    private void verificarUsuarioLocked(Long usuarioId, String origem) {
        if (evolutionWaSessionRegistry.isUserDisconnected(usuarioId)) {
            String instance = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
            persistirEstado(usuarioId, instance, WhatsappConexaoEstados.SUPPRESSED,
                "desligado pelo utilizador", origem);
            return;
        }
        String instance = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        Probe probe = sondar(usuarioId, instance);
        persistirEstado(usuarioId, instance, probe.estado, probe.detalhe, origem);
        if (WhatsappConexaoEstados.isQueda(probe.estado)) {
            tentarReconectar(usuarioId, instance, false);
        } else if (WhatsappConexaoEstados.OPEN.equals(probe.estado) && instance != null && !instance.isBlank()) {
            try {
                evolutionInstanceLifecycleService.ensureInstanceWebhook(instance);
            } catch (Exception e) {
                log.debug("Monitor WhatsApp: webhook ensure [{}]: {}", instance,
                    LogSanitizer.sanitize(e.getMessage()));
            }
        }
    }

    private Probe sondar(Long usuarioId, String instanceName) {
        try {
            evolutionPairingService.invalidatePairingCredCache(usuarioId);
            EvolutionPairingService.ResolvedEvolutionCred cred =
                evolutionPairingService.resolveCredentials(usuarioId);
            Optional<String> raw = evolutionPairingService.fetchConnectionStateForUser(usuarioId);
            if (raw.isPresent()) {
                String estado = WhatsappConexaoEstados.normalizar(raw.get());
                if (WhatsappConexaoEstados.OPEN.equals(estado)
                    && evolutionPairingService.isGhostOpenStaleInstance(cred)) {
                    return new Probe(WhatsappConexaoEstados.CLOSE, "ghost-open-stale");
                }
                return new Probe(estado, "connectionState=" + raw.get().trim());
            }
            if (instanceName != null && !instanceName.isBlank()) {
                Optional<String> listed =
                    evolutionPairingService.fetchInstancesConnectionStatus(instanceName);
                if (listed.isEmpty()) {
                    return new Probe(WhatsappConexaoEstados.MISSING, "instância inexistente em fetchInstances");
                }
                return new Probe(WhatsappConexaoEstados.normalizar(listed.get()),
                    "fetchInstances=" + listed.get());
            }
            return new Probe(WhatsappConexaoEstados.MISSING, "sem nome de instância");
        } catch (Exception e) {
            return new Probe(WhatsappConexaoEstados.ERROR,
                "erro de rede/timeout: " + safeDetalhe(e.getMessage()));
        }
    }

    private void persistirEstado(
        Long usuarioId,
        String instanceName,
        String estadoNovo,
        String detalhe,
        String origem
    ) {
        LocalDateTime agora = AppTimeZone.agora();
        WhatsappConexaoStatus cur = statusRepository.findById(usuarioId).orElse(null);
        String anterior = cur == null ? null : cur.getEstado();
        boolean mudou = anterior == null || !anterior.equals(estadoNovo);

        if (cur == null) {
            cur = new WhatsappConexaoStatus();
            cur.setUsuarioId(usuarioId);
            cur.setEstadoDesde(agora);
            cur.setTentativasReconexao(0);
            cur.setAlertaDesconexaoEnviado(false);
        }
        if (mudou) {
            cur.setEstadoDesde(agora);
            if (WhatsappConexaoEstados.OPEN.equals(estadoNovo)) {
                boolean haviaAlerta = cur.isAlertaDesconexaoEnviado();
                cur.setTentativasReconexao(0);
                cur.setProximaTentativaEm(null);
                cur.setUltimoResultadoReconexao("ok");
                if (haviaAlerta) {
                    enviarRecuperacao(instanceName);
                }
                cur.setAlertaDesconexaoEnviado(false);
            }
            WhatsappConexaoTransicao tr = new WhatsappConexaoTransicao();
            tr.setUsuarioId(usuarioId);
            tr.setInstanceName(instanceName);
            tr.setEstadoAnterior(anterior);
            tr.setEstadoNovo(estadoNovo);
            tr.setDetalhe(trimDetalhe(detalhe));
            tr.setOrigem(origem == null ? "poll" : origem);
            tr.setVerificadoEm(agora);
            transicaoRepository.save(tr);
        }
        cur.setInstanceName(instanceName);
        cur.setEstado(estadoNovo);
        cur.setDetalhe(trimDetalhe(detalhe));
        cur.setVerificadoEm(agora);
        statusRepository.save(cur);
    }

    /**
     * Reconecta via connect + restart. Nunca faz logout/delete/recreate.
     */
    private void tentarReconectar(Long usuarioId, String instanceName, boolean forcar) {
        if (instanceName == null || instanceName.isBlank()) {
            return;
        }
        if (evolutionWaSessionRegistry.isUserDisconnected(usuarioId)) {
            return;
        }
        WhatsappConexaoStatus cur = statusRepository.findById(usuarioId).orElse(null);
        LocalDateTime agora = AppTimeZone.agora();
        int max = Math.max(1, properties.getMaxTentativas());
        if (!forcar && cur != null) {
            if (cur.getTentativasReconexao() >= max) {
                talvezAlertar(cur, instanceName);
                return;
            }
            if (cur.getProximaTentativaEm() != null && cur.getProximaTentativaEm().isAfter(agora)) {
                return;
            }
        }

        log.warn("Monitor WhatsApp: a reconectar instance={} userId={} forcar={}",
            instanceName, usuarioId, forcar);
        boolean ok = false;
        String resultado = "falha";
        try {
            evolutionInstanceLifecycleService.ensureInstanceWebhook(instanceName);
            ok = evolutionPairingService.attemptSessionReconnect(instanceName);
            if (!ok) {
                evolutionInstanceSettingsService.restartInstance(instanceName);
                ok = evolutionPairingService.attemptSessionReconnect(instanceName);
            }
            resultado = ok ? "ok" : "falha";
        } catch (Exception e) {
            resultado = "erro";
            log.warn("Monitor WhatsApp: reconexão [{}]: {}", instanceName,
                LogSanitizer.sanitize(e.getMessage()));
        }

        if (cur == null) {
            cur = new WhatsappConexaoStatus();
            cur.setUsuarioId(usuarioId);
            cur.setInstanceName(instanceName);
            cur.setEstado(WhatsappConexaoEstados.CLOSE);
            cur.setEstadoDesde(agora);
            cur.setAlertaDesconexaoEnviado(false);
        }
        cur.setUltimaTentativaEm(agora);
        cur.setUltimoResultadoReconexao(resultado);
        cur.setInstanceName(instanceName);

        WhatsappConexaoTransicao tr = new WhatsappConexaoTransicao();
        tr.setUsuarioId(usuarioId);
        tr.setInstanceName(instanceName);
        tr.setEstadoAnterior(cur.getEstado());
        tr.setOrigem("reconnect");
        tr.setVerificadoEm(agora);

        if (ok) {
            evolutionSessionMetricsService.recordReconnect(instanceName, forcar ? "manual" : "auto");
            evolutionInstanceSettingsService.maintainPhoneFriendlySession(instanceName);
            evolutionInstanceSettingsService.markInstanceStabilized(instanceName);
            boolean haviaAlerta = cur.isAlertaDesconexaoEnviado();
            cur.setEstado(WhatsappConexaoEstados.OPEN);
            cur.setDetalhe("reconectado");
            cur.setEstadoDesde(agora);
            cur.setTentativasReconexao(0);
            cur.setProximaTentativaEm(null);
            if (haviaAlerta) {
                enviarRecuperacao(instanceName);
            }
            cur.setAlertaDesconexaoEnviado(false);
            tr.setEstadoNovo(WhatsappConexaoEstados.OPEN);
            tr.setDetalhe("reconexão ok");
        } else {
            int next = cur.getTentativasReconexao() + 1;
            cur.setTentativasReconexao(next);
            Duration wait = WhatsappReconexaoBackoff.esperaAposFalhas(next, properties.getBackoffMinutos());
            if (next >= max) {
                cur.setProximaTentativaEm(null);
            } else {
                cur.setProximaTentativaEm(agora.plus(wait));
            }
            cur.setEstado(WhatsappConexaoEstados.CLOSE);
            cur.setDetalhe("reconexão falhou (tentativa " + next + "/" + max + ")");
            tr.setEstadoNovo(WhatsappConexaoEstados.CLOSE);
            tr.setDetalhe(cur.getDetalhe());
            talvezAlertar(cur, instanceName);
        }
        cur.setVerificadoEm(agora);
        statusRepository.save(cur);
        transicaoRepository.save(tr);
    }

    private void talvezAlertar(WhatsappConexaoStatus cur, String instanceName) {
        if (cur.isAlertaDesconexaoEnviado()) {
            return;
        }
        LocalDateTime agora = AppTimeZone.agora();
        long minutosDown = cur.getEstadoDesde() == null
            ? 0L
            : Math.max(0L, Duration.between(cur.getEstadoDesde(), agora).toMinutes());
        int n = cur.getTentativasReconexao();
        boolean porTentativas = n >= Math.max(1, properties.getAlertaAposTentativas());
        boolean porTempo = minutosDown >= Math.max(1, properties.getAlertaAposMinutos());
        if (!porTentativas && !porTempo) {
            return;
        }
        String link = vinculoUrl();
        String assunto = "[ConsumoEsperto] WhatsApp desconectado há " + minutosDown
            + " min — ação necessária";
        String corpo = "A sessão WhatsApp (Evolution) está desligada.\n"
            + "Instância: " + (instanceName == null ? "(desconhecida)" : instanceName) + "\n"
            + "Tempo fora: " + minutosDown + " minuto(s)\n"
            + "Tentativas automáticas de reconexão: " + n + "\n\n"
            + "Abra a tela de vinculação e reconecte (QR ou código de 8 dígitos):\n"
            + link + "\n\n"
            + "No telemóvel: WhatsApp → Aparelhos ligados → Ligar um dispositivo "
            + "(ou «Ligar com número de telefone»).\n"
            + "O monitor não apaga a instância; se o pareamento tiver expirado no WhatsApp, "
            + "é preciso um QR ou código novo.";
        alertaOperacionalService.alertar(
            AlertaOperacionalService.TIPO_WHATSAPP_DESCONECTADO,
            corpo,
            assunto,
            true
        );
        cur.setAlertaDesconexaoEnviado(true);
        statusRepository.save(cur);
    }

    private void enviarRecuperacao(String instanceName) {
        String hora = AppTimeZone.agora().toLocalTime().withNano(0).toString();
        String assunto = "[ConsumoEsperto] WhatsApp reconectado às " + hora;
        String corpo = "A conexão WhatsApp foi restabelecida às " + hora + ".\n"
            + "Instância: " + (instanceName == null ? "(desconhecida)" : instanceName) + "\n"
            + "Tela de vinculação: " + vinculoUrl();
        alertaOperacionalService.alertar(
            AlertaOperacionalService.TIPO_WHATSAPP_RECUPERADO,
            corpo,
            assunto,
            false
        );
    }

    private String vinculoUrl() {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/whatsapp-config";
    }

    private Object lockFor(Long usuarioId) {
        return locks.computeIfAbsent(usuarioId, id -> new Object());
    }

    private static String trimDetalhe(String detalhe) {
        if (detalhe == null) {
            return null;
        }
        String s = LogSanitizer.sanitize(detalhe);
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static String safeDetalhe(String raw) {
        if (raw == null) {
            return "erro";
        }
        return LogSanitizer.sanitize(raw);
    }

    private static final class Probe {
        final String estado;
        final String detalhe;

        Probe(String estado, String detalhe) {
            this.estado = estado;
            this.detalhe = detalhe;
        }
    }
}
