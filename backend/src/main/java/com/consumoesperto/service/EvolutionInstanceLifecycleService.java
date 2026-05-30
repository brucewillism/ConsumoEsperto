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
    private final EvolutionWaSessionRegistry evolutionWaSessionRegistry;

    public EvolutionInstanceLifecycleService(
        UsuarioAiConfigRepository usuarioAiConfigRepository,
        UsuarioRepository usuarioRepository,
        EvolutionPairingService evolutionPairingService,
        EvolutionInstanceSettingsService evolutionInstanceSettingsService,
        EvolutionWaSessionRegistry evolutionWaSessionRegistry
    ) {
        this.usuarioAiConfigRepository = usuarioAiConfigRepository;
        this.usuarioRepository = usuarioRepository;
        this.evolutionPairingService = evolutionPairingService;
        this.evolutionInstanceSettingsService = evolutionInstanceSettingsService;
        this.evolutionWaSessionRegistry = evolutionWaSessionRegistry;
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
        String instanceName = rotateGhostOpenInstanceIfNeeded(usuarioId, assign.instanceName());
        boolean nameRotated = !instanceName.equals(assign.instanceName());
        if (!apiConfigured()) {
            log.warn("Evolution não configurada — instância {} não criada na API", instanceName);
            evolutionPairingService.invalidatePairingCredCache(usuarioId);
            return new PrepareInstanceResult(
                instanceName,
                true,
                Optional.of("Evolution API não configurada (evolution.url / evolution.apikey)")
            );
        }
        EnsureInstanceOutcome ensure = ensureEvolutionInstanceReady(instanceName);
        boolean skipLogout = assign.newlyAssignedDedicated() || nameRotated || ensure.freshInstance();
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        return new PrepareInstanceResult(instanceName, skipLogout, ensure.warning());
    }

    /**
     * Instância com {@code connectionState=open} mas {@code fetchInstances.connectionStatus=close}
     * não gera QR — apaga ou renomeia (ex. ce-u1 → ce-u1-r1716910000).
     */
    @Transactional
    protected String rotateGhostOpenInstanceIfNeeded(Long usuarioId, String instanceName) {
        if (!apiConfigured() || usuarioId == null || instanceName == null || instanceName.isBlank()) {
            return instanceName;
        }
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        EvolutionPairingService.ResolvedEvolutionCred cred = evolutionPairingService.resolveCredentials(usuarioId);
        if (!evolutionPairingService.isGhostOpenStaleInstance(cred)) {
            return instanceName;
        }
        log.warn("Evolution instância {} em estado fantasma (open/close) — a recuperar para QR", instanceName);
        return recycleStaleInstance(usuarioId, instanceName, true);
    }

    /**
     * Logout + delete; se delete falhar ou for preciso nome novo, grava {@code ce-uN-r<epoch>} e recria instância.
     */
    @Transactional
    protected String recycleStaleInstance(Long usuarioId, String instanceName, boolean forceRename) {
        if (!apiConfigured() || instanceName == null || instanceName.isBlank()) {
            return instanceName;
        }
        logoutInstanceQuietly(instanceName);
        pauseMillis(1_500);
        boolean deleted = deleteInstance(instanceName);
        String target = instanceName;
        if (!deleted || forceRename) {
            String base = instanceName.replaceAll("-r\\d+$", "");
            target = base + "-r" + (System.currentTimeMillis() / 1000L);
            if (usuarioId != null && !target.equals(instanceName)) {
                UsuarioAiConfig cfg = usuarioAiConfigRepository.findByUsuarioId(usuarioId)
                    .orElseGet(() -> newConfigFor(usuarioId));
                cfg.setEvolutionInstanceName(target);
                usuarioAiConfigRepository.save(cfg);
                log.info("Evolution: utilizador {} migrado para instância {}", usuarioId, target);
            }
        }
        recreateInstanceForQrPairing(target);
        if (usuarioId != null) {
            evolutionPairingService.invalidatePairingCredCache(usuarioId);
            evolutionWaSessionRegistry.markInstanceRecreateDone(usuarioId);
        }
        return target;
    }

    /**
     * Desliga só a sessão WhatsApp na Evolution (logout + presença unavailable + restart).
     * Mantém o número gravado na app — diferente de {@link #releaseInstanceOnUnlink}.
     */
    @Transactional
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
        pauseMillis(1_500);

        boolean deleted = deleteInstance(instanceName);
        out.put("instanceDeleted", deleted);

        EvolutionPairingService.ResolvedEvolutionCred credAfterLogout =
            evolutionPairingService.resolveCredentials(usuarioId);
        boolean ghost = evolutionPairingService.isGhostOpenStaleInstance(credAfterLogout);
        out.put("ghostInstanceDetected", ghost);

        String finalInstance = instanceName;
        boolean rotated = false;
        evolutionInstanceSettingsService.restartInstance(instanceName);
        pauseMillis(2_000);
        evolutionPairingService.clearPairingMaterialCache(instanceName);
        if (!deleted || ghost) {
            finalInstance = recycleStaleInstance(usuarioId, instanceName, !deleted || ghost);
            rotated = !finalInstance.equals(instanceName);
            out.put("instanceRotated", rotated);
            out.put("instanceName", finalInstance);
            deleted = instanceExistsInEvolution(finalInstance);
            out.put("instanceDeleted", deleted);
        } else {
            out.put("instanceRotated", false);
        }

        evolutionPairingService.markWaSessionDisconnectedByUser(usuarioId);
        out.put("sessionMarkedDisconnected", true);

        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        Optional<String> after = evolutionPairingService.fetchConnectionStateForUser(usuarioId);
        after.ifPresent(s -> out.put("connectionStateAfter", s));
        EvolutionPairingService.ResolvedEvolutionCred credFinal =
            evolutionPairingService.resolveCredentials(usuarioId);
        boolean apiStillOpen = evolutionPairingService.isRealWaSessionOpen(credFinal)
            || after.filter(this::connectionStateLooksConnected).isPresent();
        out.put("evolutionApiReportsOpen", apiStillOpen);

        out.put("evolutionWaConnected", false);
        boolean needsWarning = apiStillOpen || ghost || rotated || !Boolean.TRUE.equals(out.get("logoutRequested"));
        out.put("status", needsWarning ? "warning" : "success");
        StringBuilder msg = new StringBuilder("Sessão marcada como desligada na app.");
        if (rotated) {
            msg.append(" Instância migrada para ").append(finalInstance).append(" (a anterior estava bloqueada na Evolution).");
        } else if (!deleted && !rotated) {
            msg.append(" A Evolution não apagou a instância antiga — tente Atualizar vínculo para obter QR numa instância nova.");
        }
        if (apiStillOpen) {
            msg.append(" O servidor ainda reporta sessão activa: use Atualizar vínculo e escaneie o QR novo.");
        } else {
            msg.append(" Use Atualizar vínculo e escaneie o QR para ligar de novo.");
        }
        out.put("message", msg.toString());
        log.info(
            "Evolution disconnect userId={} instance={} final={} deleted={} rotated={} ghost={} apiStillOpen={}",
            usuarioId, instanceName, finalInstance, deleted, rotated, ghost, apiStillOpen
        );
        return out;
    }

    /**
     * Ao desvincular: encerra sessão WhatsApp na instância (libera slot para novo QR).
     */
    @Transactional
    public void releaseInstanceOnUnlink(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        disconnectEvolutionSession(usuarioId);
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
    }

    /**
     * Prepara a instância para novo QR: restart se estiver {@code connecting}/{@code close}
     * (evita logout destrutivo que invalidava o scan após «Desligar Evolution»).
     */
    public void primeInstanceForQrConnect(Long usuarioId) {
        if (!apiConfigured() || usuarioId == null) {
            return;
        }
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        if (instanceName == null || instanceName.isBlank()) {
            return;
        }
        if (evolutionPairingService.isInstanceConnectedForUser(usuarioId)) {
            return;
        }
        Optional<String> listed = evolutionPairingService.fetchInstancesConnectionStatus(instanceName);
        String st = listed.orElse("").trim().toLowerCase(Locale.ROOT);
        boolean stuck = st.isEmpty()
            || "connecting".equals(st)
            || "close".equals(st)
            || "closed".equals(st)
            || "disconnected".equals(st)
            || "disconnect".equals(st)
            || "refused".equals(st)
            || "logout".equals(st);
        if (stuck) {
            log.info("Evolution [{}] estado {} — restart antes do QR", instanceName, st.isBlank() ? "?" : st);
            evolutionInstanceSettingsService.restartInstance(instanceName);
            pauseMillis(2_500);
            evolutionPairingService.clearPairingMaterialCache(instanceName);
        }
    }

    /**
     * @deprecated Preferir {@link #primeInstanceForQrConnect}; mantido para compatibilidade interna.
     */
    @Deprecated
    public void resetSessionBeforePairing(Long usuarioId, boolean skipLogout) {
        if (skipLogout || !apiConfigured()) {
            return;
        }
        primeInstanceForQrConnect(usuarioId);
    }

    /**
     * Polling do modal QR: recria instância no máximo de 45 em 45 s quando a sessão está «desligada» na app.
     */
    public void ensureFreshInstanceWhenPairingAfterDisconnect(Long usuarioId) {
        if (usuarioId == null || !apiConfigured()) {
            return;
        }
        if (!evolutionPairingService.isWaSessionDisconnectedByUser(usuarioId)) {
            return;
        }
        if (!evolutionWaSessionRegistry.tryAcquireInstanceRecreateForQr(usuarioId, 45_000)) {
            return;
        }
        String instanceName = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        if (instanceName == null || instanceName.isBlank()) {
            return;
        }
        log.info("Evolution QR pairing: recriar instância {} (utilizador {})", instanceName, usuarioId);
        recreateInstanceForQrPairing(instanceName);
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
    }

    /** Logout + delete + create — liberta estado «open» fantasma que impede QR. */
    private void recreateInstanceForQrPairing(String instanceName) {
        logoutInstanceQuietly(instanceName);
        deleteInstance(instanceName);
        pauseMillis(2_000);
        createInstanceIfAbsent(instanceName);
        configureInstanceWebhookQuietly(instanceName);
        applyGhostPrivacySettingsQuietly(instanceName);
        evolutionInstanceSettingsService.restartInstance(instanceName);
        pauseMillis(2_000);
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
            String resp = evolutionPostJsonReturning(url, body);
            ingestCreateResponse(instanceName, resp);
            log.info("Evolution instância criada ou confirmada: {}", instanceName);
        } catch (HttpClientErrorException e) {
            ingestCreateResponse(instanceName, e.getResponseBodyAsString());
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
                String altResp = evolutionPostJsonReturning(altUrl, altBody);
                ingestCreateResponse(instanceName, altResp);
                log.info("Evolution instância criada via path alternativo: {}", instanceName);
            } catch (HttpClientErrorException e2) {
                ingestCreateResponse(instanceName, e2.getResponseBodyAsString());
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

    private void ingestCreateResponse(String instanceName, String jsonBody) {
        if (jsonBody != null && !jsonBody.isBlank()) {
            evolutionPairingService.ingestPairingJsonFromCreateOrConnect(instanceName, jsonBody);
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
        if (instanceName == null || instanceName.isBlank()) {
            return true;
        }
        String trimmed = instanceName.trim();
        if (!instanceExistsInEvolution(trimmed)) {
            return true;
        }
        logoutInstanceQuietly(trimmed);
        pauseMillis(800);
        for (HttpMethod method : new HttpMethod[] { HttpMethod.DELETE, HttpMethod.POST }) {
            try {
                String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/delete/" + trimmed);
                evolutionRequest(url, method, null);
                pauseMillis(500);
                if (!instanceExistsInEvolution(trimmed)) {
                    log.info("Evolution delete instance ({}) {}", method, trimmed);
                    return true;
                }
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    return true;
                }
                log.debug("Evolution delete [{}] {}: HTTP {}", trimmed, method, e.getRawStatusCode());
            } catch (Exception ex) {
                log.debug("Evolution delete [{}] {}: {}", trimmed, method, ex.getMessage());
            }
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/delete");
            Map<String, Object> body = Map.of("instanceName", trimmed);
            evolutionRequest(url, HttpMethod.DELETE, body);
            pauseMillis(500);
            if (!instanceExistsInEvolution(trimmed)) {
                log.info("Evolution delete instance (DELETE body) {}", trimmed);
                return true;
            }
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() == 404) {
                return true;
            }
            log.debug("Evolution delete [{}] DELETE+body: HTTP {}", trimmed, e.getRawStatusCode());
        } catch (Exception ex) {
            log.debug("Evolution delete [{}] DELETE+body: {}", trimmed, ex.getMessage());
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

    private String evolutionPostJsonReturning(String url, Map<String, Object> body) {
        HttpHeaders headers = evolutionHeaders();
        ResponseEntity<String> resp = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return resp.getBody();
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
