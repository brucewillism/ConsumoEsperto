package com.consumoesperto.service;

import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.util.EvolutionUrlSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import org.springframework.scheduling.TaskScheduler;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Modo "fantasma" na Evolution API: não força online, não marca mensagens/status como lidos,
 * não sincroniza histórico completo.
 * <p>
 * Nota: {@code alwaysOnline} / {@code markOnlineOnConnect} só passam a valer após
 * {@code POST /instance/restart/{instance}}. {@code readMessages} atualiza na sessão activa,
 * mas reiniciar garante estado limpo.
 */
@Service
public class EvolutionInstanceSettingsService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionInstanceSettingsService.class);

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;

    public EvolutionInstanceSettingsService(
        UsuarioAiConfigRepository usuarioAiConfigRepository,
        ObjectMapper objectMapper,
        TaskScheduler taskScheduler
    ) {
        this.usuarioAiConfigRepository = usuarioAiConfigRepository;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    private RestTemplate restTemplate;

    private static final long CONNECT_PRIVACY_DEBOUNCE_MS = 120_000L;
    private final ConcurrentHashMap<String, Long> lastConnectPrivacyApplyMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> presenceAfterActivityTasks = new ConcurrentHashMap<>();
    /** Instâncias já estabilizadas em modo sticky — não reiniciar nem mexer em presença até desligar. */
    private final Set<String> stabilizedInstances = ConcurrentHashMap.newKeySet();

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:ConsumoEsperto}")
    private String defaultEvolutionInstance;

    @Value("${consumoesperto.evolution.privacy.always-online:false}")
    private boolean alwaysOnline;

    @Value("${consumoesperto.evolution.privacy.read-messages:false}")
    private boolean readMessages;

    @Value("${consumoesperto.evolution.privacy.read-status:false}")
    private boolean readStatus;

    @Value("${consumoesperto.evolution.privacy.sync-full-history:false}")
    private boolean syncFullHistory;

    /** Reinicia a instância após settings/set para recarregar markOnlineOnConnect no Baileys. */
    @Value("${consumoesperto.evolution.privacy.restart-after-apply:false}")
    private boolean restartAfterApply;

    /** Reinicia ao detectar ligação se settings não reflectirem alwaysOnline/readMessages off. */
    @Value("${consumoesperto.evolution.privacy.restart-on-connect:false}")
    private boolean restartOnConnect;

    /** Após ligar, força presença unavailable (não ficar "online" na lista de contactos). */
    @Value("${consumoesperto.evolution.privacy.set-unavailable-presence:false}")
    private boolean setUnavailablePresence;

    @Value("${consumoesperto.evolution.privacy.presence-refresh-after-message-ms:0}")
    private long presenceRefreshAfterMessageMs;

    /**
     * Mantém a sessão ligada até desligar na app: evita restart/presença periódica que derruba o Baileys.
     */
    @Value("${consumoesperto.evolution.session.sticky:true}")
    private boolean sessionSticky;

    public void markInstanceStabilized(String instanceName) {
        if (instanceName != null && !instanceName.isBlank()) {
            stabilizedInstances.add(instanceName.trim());
        }
    }

    public void clearInstanceStabilized(String instanceName) {
        if (instanceName != null && !instanceName.isBlank()) {
            stabilizedInstances.remove(instanceName.trim());
        }
    }

    public boolean isInstanceStabilized(String instanceName) {
        return instanceName != null && !instanceName.isBlank() && stabilizedInstances.contains(instanceName.trim());
    }

    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(25_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * Propriedades para {@code POST /instance/create} e {@code POST /settings/set/{instance}}.
     * {@code msgCall} é obrigatório na API v2 mesmo com {@code rejectCall=false}.
     */
    public Map<String, Object> privacySettingsForCreate() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rejectCall", false);
        m.put("msgCall", "");
        m.put("groupsIgnore", false);
        m.put("alwaysOnline", alwaysOnline);
        m.put("readMessages", readMessages);
        m.put("readStatus", readStatus);
        m.put("syncFullHistory", syncFullHistory);
        return m;
    }

    /**
     * Aplica settings, verifica na Evolution, reinicia instância e tenta presença unavailable.
     */
    public Map<String, Object> applyGhostPrivacySettingsDetailed(String instanceName) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("instance", instanceName);
        report.put("requested", privacySettingsForCreate());

        if (instanceName == null || instanceName.isBlank()) {
            report.put("status", "error");
            report.put("message", "Nome da instância Evolution vazio");
            return report;
        }
        if (!apiConfigured()) {
            report.put("status", "error");
            report.put("message", "Evolution API não configurada (evolution.url / evolution.apikey)");
            return report;
        }

        String name = instanceName.trim();
        try {
            String setUrl = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "settings/set/" + name);
            evolutionPostJson(setUrl, privacySettingsForCreate());
            report.put("settingsSet", true);
        } catch (HttpClientErrorException e) {
            report.put("status", "error");
            report.put("message", "settings/set HTTP " + e.getRawStatusCode() + ": "
                + abbreviate(e.getResponseBodyAsString(), 200));
            return report;
        } catch (Exception ex) {
            report.put("status", "error");
            report.put("message", "settings/set: " + ex.getMessage());
            return report;
        }

        Optional<JsonNode> found = fetchSettings(name);
        found.ifPresent(node -> report.put("settingsInEvolution", node));
        boolean settingsOk = found.map(this::settingsMatchGhostMode).orElse(false);
        report.put("settingsVerified", settingsOk);
        if (!settingsOk && found.isPresent()) {
            log.warn(
                "Evolution [{}]: settings/find não reflecte modo fantasma — verifique Manager ou versão da API",
                name
            );
        }

        if (restartAfterApply && !sessionSticky) {
            boolean restarted = restartInstanceQuietly(name);
            report.put("instanceRestarted", restarted);
        }

        if (setUnavailablePresence && !sessionSticky) {
            boolean presence = applyPresenceUnavailable(name);
            report.put("presenceUnavailable", presence);
        }

        report.put("status", settingsOk ? "success" : "warning");
        report.put(
            "message",
            settingsOk
                ? "Privacidade aplicada (sem alwaysOnline/readMessages). Notificações no telemóvel devem voltar em ~1 min."
                : "Settings gravados mas a Evolution não confirmou os valores — reinicie a instância no Manager ou aguarde o job automático."
        );
        log.info("Evolution privacidade [{}]: verified={} restart={}", name, settingsOk, restartAfterApply);
        return report;
    }

    public Optional<String> applyGhostPrivacySettings(String instanceName) {
        Map<String, Object> r = applyGhostPrivacySettingsDetailed(instanceName);
        String status = String.valueOf(r.getOrDefault("status", ""));
        if ("success".equals(status) || "warning".equals(status)) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(r.getOrDefault("message", "Falha ao aplicar settings")));
    }

    /**
     * Chamado quando a instância fica {@code open}: desliga alwaysOnline/readMessages na Evolution
     * (preserva notificações no telemóvel) e força presença unavailable.
     */
    public void onInstanceConnected(String instanceName) {
        ensurePhoneFriendlyOnConnect(instanceName);
    }

    /**
     * Garante settings de privacidade + presença unavailable após QR/conexão (debounce 2 min por instância).
     */
    public void ensurePhoneFriendlyOnConnect(String instanceName) {
        if (instanceName == null || instanceName.isBlank() || !apiConfigured()) {
            return;
        }
        String name = instanceName.trim();
        if (sessionSticky && stabilizedInstances.contains(name)) {
            log.debug("Evolution [{}]: sessão sticky estabilizada — sem restart/presença", name);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastConnectPrivacyApplyMs.get(name);
        boolean debounced = last != null && now - last < CONNECT_PRIVACY_DEBOUNCE_MS;
        if (!debounced) {
            lastConnectPrivacyApplyMs.put(name, now);
            applyPrivacySettingsQuietly(name);
            if (!sessionSticky) {
                Optional<JsonNode> found = fetchSettings(name);
                boolean settingsOk = found.map(this::settingsMatchGhostMode).orElse(false);
                if (!settingsOk && restartOnConnect) {
                    restartInstanceQuietly(name);
                    applyPrivacySettingsQuietly(name);
                }
            }
        }
        if (setUnavailablePresence && !sessionSticky) {
            applyPresenceUnavailable(name);
            schedulePresenceRetries(name);
        }
        if (sessionSticky) {
            stabilizedInstances.add(name);
            log.info("Evolution [{}]: sessão sticky estabilizada após ligação", name);
        }
    }

    /**
     * Após actividade do bot (webhook), WhatsApp volta a «online» — reagenda unavailable (~1 min).
     */
    public void schedulePresenceRefreshAfterActivity(String instanceName) {
        if (sessionSticky || presenceRefreshAfterMessageMs <= 0) {
            return;
        }
        if (!setUnavailablePresence || instanceName == null || instanceName.isBlank() || !apiConfigured()) {
            return;
        }
        if (taskScheduler == null) {
            applyPresenceUnavailable(instanceName.trim());
            return;
        }
        String name = instanceName.trim();
        long delay = Math.max(5_000L, presenceRefreshAfterMessageMs);
        ScheduledFuture<?> prev = presenceAfterActivityTasks.remove(name);
        if (prev != null) {
            prev.cancel(false);
        }
        ScheduledFuture<?> scheduled = taskScheduler.schedule(
            () -> {
                presenceAfterActivityTasks.remove(name);
                applyPresenceUnavailable(name);
            },
            Instant.now().plusMillis(delay)
        );
        presenceAfterActivityTasks.put(name, scheduled);
    }

    /** Job periódico: instâncias open voltam a presença unavailable (notificações no telemóvel). */
    public void refreshPresenceForConnectedInstances() {
        if (sessionSticky || !setUnavailablePresence || !apiConfigured()) {
            return;
        }
        for (String name : fetchConnectedInstanceNames()) {
            applyPresenceUnavailable(name);
        }
    }

    private void applyPrivacySettingsQuietly(String instanceName) {
        try {
            String setUrl = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "settings/set/" + instanceName);
            evolutionPostJson(setUrl, privacySettingsForCreate());
        } catch (Exception ex) {
            log.warn("Evolution settings/set [{}] on connect: {}", instanceName, ex.getMessage());
        }
    }

    private void schedulePresenceRetries(String instanceName) {
        if (taskScheduler == null) {
            return;
        }
        long[] delaysMs = {5_000L, 45_000L, 120_000L};
        for (long delay : delaysMs) {
            taskScheduler.schedule(
                () -> applyPresenceUnavailable(instanceName),
                Instant.now().plusMillis(delay)
            );
        }
    }

    private Set<String> fetchConnectedInstanceNames() {
        Set<String> out = new LinkedHashSet<>();
        if (!apiConfigured()) {
            return out;
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/fetchInstances");
            HttpHeaders headers = evolutionHeaders();
            String body = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            ).getBody();
            if (body == null || body.isBlank()) {
                return out;
            }
            JsonNode root = objectMapper.readTree(body);
            collectConnectedInstanceNames(root, out);
        } catch (Exception ex) {
            log.debug("fetchInstances for presence refresh: {}", ex.getMessage());
        }
        return out;
    }

    private void collectConnectedInstanceNames(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectConnectedInstanceNames(item, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String name = node.path("name").asText("").trim();
        if (name.isBlank()) {
            name = node.path("instanceName").asText("").trim();
        }
        if (name.isBlank()) {
            name = node.path("instance").path("instanceName").asText("").trim();
        }
        String status = node.path("connectionStatus").asText("").trim();
        if (status.isBlank()) {
            status = node.path("state").asText("").trim();
        }
        if (!name.isBlank() && isConnectionOpen(status)) {
            out.add(name);
        }
        node.fields().forEachRemaining(e -> collectConnectedInstanceNames(e.getValue(), out));
    }

    private static boolean isConnectionOpen(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        return "open".equals(s) || "connected".equals(s);
    }

    public Map<String, Object> applyGhostPrivacyToAllKnownInstances() {
        Set<String> names = collectAllInstanceNames();
        List<Map<String, Object>> results = new ArrayList<>();
        int ok = 0;
        int warn = 0;
        int fail = 0;
        for (String name : names) {
            Map<String, Object> row = applyGhostPrivacySettingsDetailed(name);
            results.add(row);
            String st = String.valueOf(row.getOrDefault("status", ""));
            if ("success".equals(st)) {
                ok++;
            } else if ("warning".equals(st)) {
                warn++;
            } else {
                fail++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", names.size());
        out.put("ok", ok);
        out.put("warning", warn);
        out.put("failed", fail);
        out.put("instances", results);
        return out;
    }

    public Optional<JsonNode> fetchSettings(String instanceName) {
        if (!apiConfigured() || instanceName == null || instanceName.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "settings/find/" + instanceName.trim());
            HttpHeaders headers = evolutionHeaders();
            ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            if (resp.getBody() == null || resp.getBody().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(resp.getBody()));
        } catch (Exception ex) {
            log.debug("settings/find [{}]: {}", instanceName, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Reinicia instância na Evolution (limpa estado “open” fantasma após logout). */
    public boolean restartInstance(String instanceName) {
        return restartInstanceQuietly(instanceName);
    }

    public boolean applyPresenceUnavailable(String instanceName) {
        if (!apiConfigured() || instanceName == null || instanceName.isBlank()) {
            return false;
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(
                evolutionUrl, "instance/setPresence/" + instanceName.trim()
            );
            evolutionPostJson(url, Map.of("presence", "unavailable"));
            log.info("Evolution presença unavailable aplicada em {}", instanceName);
            return true;
        } catch (Exception ex) {
            log.debug("setPresence unavailable [{}]: {}", instanceName, ex.getMessage());
            return false;
        }
    }

    private boolean restartInstanceQuietly(String instanceName) {
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/restart/" + instanceName);
            evolutionPostJson(url, Map.of());
            log.info("Evolution instância reiniciada para recarregar settings: {}", instanceName);
            return true;
        } catch (Exception ex) {
            log.warn("Evolution restart [{}]: {}", instanceName, ex.getMessage());
            return false;
        }
    }

    private Set<String> collectAllInstanceNames() {
        Set<String> names = new LinkedHashSet<>();
        if (defaultEvolutionInstance != null && !defaultEvolutionInstance.isBlank()) {
            names.add(defaultEvolutionInstance.trim());
        }
        usuarioAiConfigRepository.findAll().forEach(cfg -> {
            if (cfg.getEvolutionInstanceName() != null && !cfg.getEvolutionInstanceName().isBlank()) {
                names.add(cfg.getEvolutionInstanceName().trim());
            }
        });
        names.addAll(fetchInstanceNamesFromEvolution());
        return names;
    }

    private boolean settingsMatchGhostMode(JsonNode root) {
        JsonNode s = settingsNode(root);
        if (s == null || s.isMissingNode()) {
            return false;
        }
        return boolField(s, "readMessages", "read_messages") == readMessages
            && boolField(s, "alwaysOnline", "always_online") == alwaysOnline
            && boolField(s, "readStatus", "read_status") == readStatus
            && boolField(s, "syncFullHistory", "sync_full_history") == syncFullHistory;
    }

    private JsonNode settingsNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.has("readMessages") || root.has("read_messages") || root.has("alwaysOnline")) {
            return root;
        }
        JsonNode inner = root.path("settings");
        if (inner.has("readMessages") || inner.has("read_messages")) {
            return inner;
        }
        return root.path("setting");
    }

    private static boolean boolField(JsonNode s, String camel, String snake) {
        if (s.has(camel)) {
            return s.path(camel).asBoolean(false);
        }
        if (s.has(snake)) {
            return s.path(snake).asBoolean(false);
        }
        return false;
    }

    private Set<String> fetchInstanceNamesFromEvolution() {
        Set<String> out = new LinkedHashSet<>();
        if (!apiConfigured()) {
            return out;
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/fetchInstances");
            HttpHeaders headers = evolutionHeaders();
            String body = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            ).getBody();
            if (body == null || body.isBlank()) {
                return out;
            }
            JsonNode root = objectMapper.readTree(body);
            collectInstanceNames(root, out);
        } catch (Exception ex) {
            log.debug("fetchInstances para privacy settings: {}", ex.getMessage());
        }
        return out;
    }

    private void collectInstanceNames(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectInstanceNames(item, out);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode name = node.path("instance").path("instanceName");
            if (name.isMissingNode() || name.isNull()) {
                name = node.path("instanceName");
            }
            if (name.isMissingNode() || name.isNull()) {
                name = node.path("name");
            }
            if (!name.isMissingNode() && !name.isNull() && name.isTextual()) {
                String n = name.asText("").trim();
                if (!n.isBlank()) {
                    out.add(n);
                }
            }
            node.fields().forEachRemaining(e -> collectInstanceNames(e.getValue(), out));
        }
    }

    private boolean apiConfigured() {
        return evolutionUrl != null
            && !evolutionUrl.isBlank()
            && evolutionApiKey != null
            && !evolutionApiKey.isBlank();
    }

    private void evolutionPostJson(String url, Map<String, Object> body) {
        HttpHeaders headers = evolutionHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    private HttpHeaders evolutionHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String key = evolutionApiKey.trim();
        headers.set("apikey", key);
        headers.setBearerAuth(key);
        return headers;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
