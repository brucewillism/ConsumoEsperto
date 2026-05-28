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
 * não sincroniza histórico completo (evita sessão Web sempre activa e notificações silenciadas no telemóvel).
 *
 * @see <a href="https://doc.evolution-api.com/v2/api-reference/settings/set">POST /settings/set/{instance}</a>
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

    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(25_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * Propriedades para {@code POST /instance/create} (Evolution v2 aceita inline).
     */
    public Map<String, Object> privacySettingsForCreate() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rejectCall", false);
        m.put("groupsIgnore", false);
        m.put("alwaysOnline", alwaysOnline);
        m.put("readMessages", readMessages);
        m.put("readStatus", readStatus);
        m.put("syncFullHistory", syncFullHistory);
        return m;
    }

    /**
     * Aplica {@code POST /settings/set/{instance}} — corrige instâncias já existentes.
     */
    public Optional<String> applyGhostPrivacySettings(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            return Optional.of("Nome da instância Evolution vazio");
        }
        if (!apiConfigured()) {
            return Optional.of("Evolution API não configurada (evolution.url / evolution.apikey)");
        }
        String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "settings/set/" + instanceName.trim());
        try {
            evolutionPostJson(url, privacySettingsForCreate());
            log.info(
                "Evolution settings modo fantasma aplicados em {} (alwaysOnline={}, readMessages={}, readStatus={}, syncFullHistory={})",
                instanceName,
                alwaysOnline,
                readMessages,
                readStatus,
                syncFullHistory
            );
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            String msg = "HTTP " + e.getRawStatusCode() + " em settings/set: "
                + abbreviate(e.getResponseBodyAsString(), 180);
            log.warn("Evolution settings/set [{}]: {}", instanceName, msg);
            return Optional.of(msg);
        } catch (Exception ex) {
            log.warn("Evolution settings/set [{}]: {}", instanceName, ex.getMessage());
            return Optional.of(ex.getMessage());
        }
    }

    /**
     * Correcção global: todas as instâncias listadas na Evolution + nomes gravados em {@link UsuarioAiConfig}.
     */
    public Map<String, Object> applyGhostPrivacyToAllKnownInstances() {
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

        List<Map<String, String>> results = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        for (String name : names) {
            Optional<String> err = applyGhostPrivacySettings(name);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("instance", name);
            if (err.isEmpty()) {
                row.put("status", "ok");
                ok++;
            } else {
                row.put("status", "error");
                row.put("message", err.get());
                fail++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", names.size());
        out.put("ok", ok);
        out.put("failed", fail);
        out.put("settings", privacySettingsForCreate());
        out.put("instances", results);
        return out;
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
