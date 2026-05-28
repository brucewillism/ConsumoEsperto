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

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    public EvolutionInstanceSettingsService(
        UsuarioAiConfigRepository usuarioAiConfigRepository,
        ObjectMapper objectMapper
    ) {
        this.usuarioAiConfigRepository = usuarioAiConfigRepository;
        this.objectMapper = objectMapper;
    }

    private RestTemplate restTemplate;

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
    @Value("${consumoesperto.evolution.privacy.restart-after-apply:true}")
    private boolean restartAfterApply;

    /** Após ligar, força presença unavailable (não ficar "online" na lista de contactos). */
    @Value("${consumoesperto.evolution.privacy.set-unavailable-presence:true}")
    private boolean setUnavailablePresence;

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

        if (restartAfterApply) {
            boolean restarted = restartInstanceQuietly(name);
            report.put("instanceRestarted", restarted);
        }

        if (setUnavailablePresence) {
            boolean presence = applyPresenceUnavailable(name);
            report.put("presenceUnavailable", presence);
        }

        report.put("status", settingsOk ? "success" : "warning");
        report.put(
            "message",
            settingsOk
                ? "Modo fantasma aplicado. Se o telemóvel ainda não notificar, desligue e volte a ligar o WhatsApp (novo QR)."
                : "Settings gravados mas a Evolution não confirmou os valores — reinicie a instância no Manager e escaneie o QR de novo."
        );
        log.info("Evolution modo fantasma [{}]: verified={} restart={}", name, settingsOk, restartAfterApply);
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
     * Chamado quando a instância fica {@code open} — mantém presença offline após reconnect.
     */
    public void onInstanceConnected(String instanceName) {
        if (!setUnavailablePresence || instanceName == null || instanceName.isBlank() || !apiConfigured()) {
            return;
        }
        applyPresenceUnavailable(instanceName.trim());
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
