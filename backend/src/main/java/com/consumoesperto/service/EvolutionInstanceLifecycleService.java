package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.EvolutionUrlSupport;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Garante instância Evolution dedicada por utilizador (evita partilhar um único QR/sessão WhatsApp).
 */
@Service
public class EvolutionInstanceLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionInstanceLifecycleService.class);

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final EvolutionPairingService evolutionPairingService;
    private final EvolutionInstanceSettingsService evolutionInstanceSettingsService;

    public EvolutionInstanceLifecycleService(
        UsuarioAiConfigRepository usuarioAiConfigRepository,
        UsuarioRepository usuarioRepository,
        EvolutionPairingService evolutionPairingService,
        EvolutionInstanceSettingsService evolutionInstanceSettingsService
    ) {
        this.usuarioAiConfigRepository = usuarioAiConfigRepository;
        this.usuarioRepository = usuarioRepository;
        this.evolutionPairingService = evolutionPairingService;
        this.evolutionInstanceSettingsService = evolutionInstanceSettingsService;
    }

    private RestTemplate restTemplate;

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:ConsumoEsperto}")
    private String defaultEvolutionInstance;

    /**
     * Quando true (recomendado em produção), cada utilizador recebe instância {@code ce-u{id}} na Evolution.
     */
    @Value("${consumoesperto.evolution.dedicated-instance-per-user:true}")
    private boolean dedicatedInstancePerUser;

    /** URL do webhook ConsumoEsperto registada em cada instância Evolution (além do webhook global no Compose). */
    @Value("${consumoesperto.evolution.webhook.url:http://backend:8087/api/public/evolution/webhook}")
    private String evolutionWebhookUrl;

    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(25_000);
        restTemplate = new RestTemplate(factory);
    }

    /** Resultado de {@link #prepareInstanceForPairing(Long)} — usado para evitar logout destrutivo em instância nova. */
    public static final class PrepareInstanceResult {
        private final String instanceName;
        private final boolean skipLogoutBeforeConnect;
        private final Optional<String> setupWarning;

        public PrepareInstanceResult(
            String instanceName,
            boolean skipLogoutBeforeConnect,
            Optional<String> setupWarning
        ) {
            this.instanceName = instanceName;
            this.skipLogoutBeforeConnect = skipLogoutBeforeConnect;
            this.setupWarning = setupWarning;
        }

        public String getInstanceName() {
            return instanceName;
        }

        /** Evita {@code logout} antes do primeiro QR (instância acabada de criar ou nome dedicado novo). */
        public boolean skipLogoutBeforeConnect() {
            return skipLogoutBeforeConnect;
        }

        public Optional<String> getSetupWarning() {
            return setupWarning;
        }
    }

    /**
     * Antes do pareamento: instância dedicada criada na Evolution e gravada em {@link UsuarioAiConfig}.
     */
    public PrepareInstanceResult prepareInstanceForPairing(Long usuarioId) {
        AssignResult assign = assignInstanceNameTransactional(usuarioId);
        if (!apiConfigured()) {
            log.warn("Evolution não configurada — instância {} não criada na API", assign.instanceName());
            evolutionPairingService.invalidatePairingCredCache(usuarioId);
            return new PrepareInstanceResult(
                assign.instanceName(),
                true,
                Optional.of("Evolution API não configurada (evolution.url / evolution.apikey)")
            );
        }
        EnsureInstanceOutcome ensure = ensureEvolutionInstanceReady(assign.instanceName());
        boolean skipLogout = assign.newlyAssignedDedicated() || ensure.freshInstance();
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        return new PrepareInstanceResult(assign.instanceName(), skipLogout, ensure.warning());
    }

    /**
     * Desliga só a sessão WhatsApp na Evolution (logout + presença unavailable + restart).
     * Mantém o número gravado na app — diferente de {@link #releaseInstanceOnUnlink}.
     */
    public Map<String, Object> disconnectEvolutionSession(Long usuarioId) {
        Map<String, Object> out = new LinkedHashMap<>();
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        out.put("instanceName", instanceName);

        if (!apiConfigured() || instanceName == null || instanceName.isBlank()) {
            out.put("status", "error");
            out.put("message", "Evolution API não configurada.");
            out.put("evolutionWaConnected", false);
            return out;
        }

        evolutionPairingService.fetchConnectionStateForUser(usuarioId)
            .ifPresent(s -> out.put("connectionStateBefore", s));

        boolean logoutOk = logoutInstance(instanceName);
        out.put("logoutRequested", logoutOk);
        evolutionInstanceSettingsService.applyPresenceUnavailable(instanceName);
        out.put("presenceUnavailable", true);
        boolean restarted = evolutionInstanceSettingsService.restartInstance(instanceName);
        out.put("instanceRestarted", restarted);

        boolean deleted = deleteInstance(instanceName);
        out.put("instanceDeleted", deleted);
        if (deleted) {
            pauseMillis(2_000);
            createInstanceIfAbsent(instanceName);
            configureInstanceWebhookQuietly(instanceName);
            applyGhostPrivacySettingsQuietly(instanceName);
        }

        evolutionPairingService.markWaSessionDisconnectedByUser(usuarioId);
        out.put("sessionMarkedDisconnected", true);

        Optional<String> after = evolutionPairingService.fetchConnectionStateForUser(usuarioId);
        after.ifPresent(s -> out.put("connectionStateAfter", s));
        boolean apiStillOpen = after.filter(this::connectionStateLooksConnected).isPresent();
        out.put("evolutionApiReportsOpen", apiStillOpen);

        out.put("evolutionWaConnected", false);
        out.put("status", "success");
        out.put(
            "message",
            apiStillOpen
                ? "Sessão desligada na app. A Evolution ainda pode mostrar «open» no servidor (cache) — "
                    + "use Atualizar vínculo e escaneie o QR para ligar de novo."
                : "Sessão Evolution desligada. Use Atualizar vínculo e escaneie o QR para ligar de novo."
        );
        log.info(
            "Evolution disconnect userId={} instance={} deleted={} apiStillOpen={}",
            usuarioId, instanceName, deleted, apiStillOpen
        );
        return out;
    }

    /**
     * Ao desvincular: encerra sessão WhatsApp na instância (libera slot para novo QR).
     */
    @Transactional
    public void releaseInstanceOnUnlink(Long usuarioId) {
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        if (apiConfigured() && instanceName != null && !instanceName.isBlank()) {
            logoutInstanceQuietly(instanceName);
        }
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
    }

    /**
     * Reinicia sessão antes de novo QR — apenas no vínculo inicial, não no polling do modal
     * (logout repetido impede a Evolution de gerar o QR).
     *
     * @param skipLogout quando true (instância nova), não chama {@code /instance/logout}
     */
    public void resetSessionBeforePairing(Long usuarioId, boolean skipLogout) {
        if (skipLogout || !apiConfigured()) {
            return;
        }
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        if (instanceName == null || instanceName.isBlank()) {
            return;
        }
        if (evolutionPairingService.isInstanceConnectedForUser(usuarioId)) {
            return;
        }
        logoutInstanceQuietly(instanceName);
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
    }

    private static final class AssignResult {
        private final String instanceName;
        private final boolean newlyAssignedDedicated;

        AssignResult(String instanceName, boolean newlyAssignedDedicated) {
            this.instanceName = instanceName;
            this.newlyAssignedDedicated = newlyAssignedDedicated;
        }

        String instanceName() {
            return instanceName;
        }

        boolean newlyAssignedDedicated() {
            return newlyAssignedDedicated;
        }
    }

    @Transactional
    protected AssignResult assignInstanceNameTransactional(Long usuarioId) {
        if (!dedicatedInstancePerUser || usuarioId == null) {
            return new AssignResult(defaultInstanceTrimmed(), false);
        }

        UsuarioAiConfig cfg = usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .orElseGet(() -> newConfigFor(usuarioId));

        String current = cfg.getEvolutionInstanceName();
        String sharedDefault = defaultInstanceTrimmed();
        boolean usesSharedOrEmpty = current == null
            || current.isBlank()
            || current.equalsIgnoreCase(sharedDefault);

        if (!usesSharedOrEmpty) {
            return new AssignResult(current.trim(), false);
        }

        String dedicated = dedicatedInstanceName(usuarioId);
        cfg.setEvolutionInstanceName(dedicated);
        usuarioAiConfigRepository.save(cfg);
        log.info("Instância Evolution dedicada atribuída ao utilizador {}: {}", usuarioId, dedicated);
        return new AssignResult(dedicated, true);
    }

    private static final class EnsureInstanceOutcome {
        private final boolean freshInstance;
        private final Optional<String> warning;

        EnsureInstanceOutcome(boolean freshInstance, Optional<String> warning) {
            this.freshInstance = freshInstance;
            this.warning = warning;
        }

        boolean freshInstance() {
            return freshInstance;
        }

        Optional<String> warning() {
            return warning;
        }
    }

    /**
     * Cria instância, configura webhook e confirma presença na Evolution (fora de transação JPA).
     */
    private EnsureInstanceOutcome ensureEvolutionInstanceReady(String instanceName) {
        boolean existed = instanceExistsInEvolution(instanceName);
        createInstanceIfAbsent(instanceName);
        configureInstanceWebhookQuietly(instanceName);
        applyGhostPrivacySettingsQuietly(instanceName);
        boolean existsNow = instanceExistsInEvolution(instanceName);
        boolean fresh = !existed && existsNow;
        if (!existed && !existsNow) {
            return new EnsureInstanceOutcome(
                fresh,
                Optional.of(
                    "A Evolution não listou a instância " + instanceName
                        + " após criar — verifique EVOLUTION_URL, API key e logs do contentor evolution_api."
                )
            );
        }
        return new EnsureInstanceOutcome(fresh, Optional.empty());
    }

    private boolean instanceExistsInEvolution(String instanceName) {
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/fetchInstances");
            String body = evolutionGet(url);
            if (body == null || body.isBlank()) {
                return false;
            }
            String needle = "\"" + instanceName + "\"";
            return body.contains(needle) || body.contains(instanceName);
        } catch (Exception ex) {
            log.debug("fetchInstances [{}]: {}", instanceName, ex.getMessage());
            return false;
        }
    }

    private UsuarioAiConfig newConfigFor(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));
        UsuarioAiConfig c = new UsuarioAiConfig();
        c.setUsuario(u);
        c.setProviderOrderJson("[\"GROQ\",\"OPENAI\",\"CLAUDE\",\"GEMINI\",\"DEEPSEEK\",\"OLLAMA\"]");
        return c;
    }

    private static String dedicatedInstanceName(Long usuarioId) {
        return "ce-u" + usuarioId;
    }

    private String defaultInstanceTrimmed() {
        return defaultEvolutionInstance != null && !defaultEvolutionInstance.isBlank()
            ? defaultEvolutionInstance.trim()
            : "ConsumoEsperto";
    }

    private boolean apiConfigured() {
        return evolutionUrl != null
            && !evolutionUrl.isBlank()
            && evolutionApiKey != null
            && !evolutionApiKey.isBlank();
    }

    private void createInstanceIfAbsent(String instanceName) {
        String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/create");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceName", instanceName);
        body.put("integration", "WHATSAPP-BAILEYS");
        body.put("qrcode", true);
        body.putAll(evolutionInstanceSettingsService.privacySettingsForCreate());

        try {
            evolutionPostJson(url, body);
            log.info("Evolution instância criada ou confirmada: {}", instanceName);
        } catch (HttpClientErrorException e) {
            if (isInstanceAlreadyExistsHttp(e.getRawStatusCode())) {
                log.debug("Evolution instância {} já existe (HTTP {})", instanceName, e.getRawStatusCode());
                return;
            }
            String altUrl = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/create/" + instanceName);
            try {
                Map<String, Object> altBody = new LinkedHashMap<>();
                altBody.put("integration", "WHATSAPP-BAILEYS");
                altBody.put("qrcode", true);
                altBody.putAll(evolutionInstanceSettingsService.privacySettingsForCreate());
                evolutionPostJson(altUrl, altBody);
                log.info("Evolution instância criada via path alternativo: {}", instanceName);
            } catch (HttpClientErrorException e2) {
                if (isInstanceAlreadyExistsHttp(e2.getRawStatusCode())) {
                    log.debug("Evolution instância {} já existe (alt HTTP {})", instanceName, e2.getRawStatusCode());
                } else {
                    log.warn("Evolution create instance [{}]: HTTP {} — {}", instanceName, e2.getRawStatusCode(),
                        abbreviate(e2.getResponseBodyAsString(), 200));
                }
            }
        } catch (Exception ex) {
            log.warn("Evolution create instance [{}] falhou: {}", instanceName, ex.getMessage());
        }
    }

    private void applyGhostPrivacySettingsQuietly(String instanceName) {
        evolutionInstanceSettingsService.applyGhostPrivacySettings(instanceName)
            .ifPresent(msg -> log.debug("Evolution privacy settings [{}]: {}", instanceName, msg));
    }

    private void configureInstanceWebhookQuietly(String instanceName) {
        String webhook = evolutionWebhookUrl != null ? evolutionWebhookUrl.trim() : "";
        if (webhook.isBlank()) {
            return;
        }
        String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "webhook/set/" + instanceName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", true);
        body.put("url", webhook);
        body.put("webhook_by_events", false);
        body.put("webhook_base64", true);
        body.put("events", List.of("MESSAGES_UPSERT", "CONNECTION_UPDATE"));
        try {
            evolutionPostJson(url, body);
            log.debug("Evolution webhook configurado para {}", instanceName);
        } catch (Exception ex) {
            log.debug("Evolution webhook/set [{}]: {}", instanceName, ex.getMessage());
        }
    }

    private static boolean isInstanceAlreadyExistsHttp(int status) {
        return status == 403 || status == 409;
    }

    private void logoutInstanceQuietly(String instanceName) {
        logoutInstance(instanceName);
    }

    private static void pauseMillis(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean connectionStateLooksConnected(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        return s.equals("open") || s.equals("connected") || s.equals("online");
    }

    private boolean deleteInstance(String instanceName) {
        for (HttpMethod method : new HttpMethod[] { HttpMethod.DELETE, HttpMethod.POST }) {
            try {
                String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/delete/" + instanceName);
                evolutionRequest(url, method, null);
                log.info("Evolution delete instance ({}) {}", method, instanceName);
                return true;
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    return true;
                }
                log.debug("Evolution delete [{}] {}: HTTP {}", instanceName, method, e.getRawStatusCode());
            } catch (Exception ex) {
                log.debug("Evolution delete [{}] {}: {}", instanceName, method, ex.getMessage());
            }
        }
        return false;
    }

    private boolean logoutInstance(String instanceName) {
        for (HttpMethod method : new HttpMethod[] { HttpMethod.DELETE, HttpMethod.POST }) {
            try {
                String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/logout/" + instanceName);
                evolutionRequest(url, method, null);
                log.info("Evolution logout ({}) para instância {}", method, instanceName);
                return true;
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    continue;
                }
                log.debug("Evolution logout [{}] {}: HTTP {}", instanceName, method, e.getRawStatusCode());
            } catch (Exception ex) {
                log.debug("Evolution logout [{}] {}: {}", instanceName, method, ex.getMessage());
            }
        }
        return false;
    }

    private String evolutionGet(String url) {
        HttpHeaders headers = evolutionHeaders();
        ResponseEntity<String> resp = restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return resp.getBody();
    }

    private void evolutionPostJson(String url, Map<String, Object> body) {
        evolutionRequest(url, HttpMethod.POST, body);
    }

    private HttpHeaders evolutionHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String key = evolutionApiKey.trim();
        headers.set("apikey", key);
        headers.setBearerAuth(key);
        return headers;
    }

    private void evolutionRequest(String url, HttpMethod method, Map<String, Object> body) {
        HttpHeaders headers = evolutionHeaders();
        HttpEntity<?> entity = body == null
            ? new HttpEntity<>(headers)
            : new HttpEntity<>(body, headers);
        restTemplate.exchange(url, method, entity, String.class);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
