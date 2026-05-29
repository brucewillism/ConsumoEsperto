package com.consumoesperto.service;

import com.consumoesperto.dto.EvolutionPairingOutcomeDTO;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.EvolutionUrlSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pareamento WhatsApp na Evolution API (QR / código) — usando {@code evolution.url}, {@code evolution.apikey}
 * e opcionalmente instância e chave próprias do utilizador ({@link UsuarioAiConfig}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvolutionPairingService {

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final EvolutionWaSessionRegistry evolutionWaSessionRegistry;

    private RestTemplate restTemplate;

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:ConsumoEsperto}")
    private String defaultEvolutionInstance;

    /**
     * Tentativas de GET /instance/connect (a Evolution faz delay fixo ~2 s após connect interno quando state=close — ver {@code InstanceController}). Valor por defeito 6 × 4 s permite ~22 s até desistir.
     */
    @Value("${evolution.pairing.connect.retries:6}")
    private int connectRetries;

    @Value("${evolution.pairing.connect.pauseMs:4000}")
    private long connectPauseMs;

    /** TTL do cache credenciais + número WhatsApp (evita avalanche Hibernate no polling / 5s). */
    @Value("${evolution.pairing.cred-cache-ms:25000}")
    private long pairingCredCacheMs;

    /** Quando true, regista WARN com corpo JSON sanitizado sempre que não há QR/pairingCode mas há aviso Evolution. */
    @Value("${evolution.pairing.log-weak-response:false}")
    private boolean logWeakEvolutionResponses;

    @Value("${consumoesperto.evolution.public-url:}")
    private String evolutionPublicUrl;

    @Value("${consumoesperto.evolution.manager-url:http://127.0.0.1:8585/manager}")
    private String evolutionManagerUrl;

    private final ConcurrentHashMap<Long, PairingCredCacheEntry> pairingCredCache = new ConcurrentHashMap<>();

    /** QR devolvido em POST /instance/create (TTL curto). */
    private final ConcurrentHashMap<String, CachedInstancePairing> recentPairingByInstance = new ConcurrentHashMap<>();

    private static final class CachedInstancePairing {
        final EvolutionPairingOutcomeDTO outcome;
        final long expiresAtMs;

        CachedInstancePairing(EvolutionPairingOutcomeDTO outcome, long expiresAtMs) {
            this.outcome = outcome;
            this.expiresAtMs = expiresAtMs;
        }

        boolean fresh() {
            return System.currentTimeMillis() < expiresAtMs;
        }
    }

    /** Invalida cache de credenciais após mudança de instância Evolution (vínculo/desvínculo). */
    public void invalidatePairingCredCache(Long usuarioId) {
        if (usuarioId != null) {
            pairingCredCache.remove(usuarioId);
        }
    }
    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * Chama Evolution {@code GET /instance/connect/:instance}, para exibir QR no cliente.
     */
    /** URL do Manager Evolution (painel web) para fallback quando REST não devolve QR. */
    public String getManagerUrlForInstance(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) {
            return "";
        }
        String base = evolutionManagerUrl != null ? evolutionManagerUrl.trim() : "";
        if (base.isBlank()) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Guarda QR/código devolvido em POST /instance/create ou connect (TTL ~2 min).
     */
    public void ingestPairingJsonFromCreateOrConnect(String instanceName, String jsonBody) {
        if (instanceName == null || instanceName.isBlank() || jsonBody == null || jsonBody.isBlank()) {
            return;
        }
        try {
            JsonNode root = unwrapTopLevelEvolutionEnvelope(objectMapper.readTree(jsonBody));
            EvolutionPairingOutcomeDTO parsed = processConnectBody(root, instanceName, true, true);
            if (parsed != null && hasUsablePairingMaterial(parsed)) {
                recentPairingByInstance.put(
                    instanceName,
                    new CachedInstancePairing(parsed, System.currentTimeMillis() + 120_000L)
                );
                log.info("Evolution [{}] QR/código em cache a partir de create/connect", instanceName);
            }
        } catch (Exception e) {
            log.debug("Evolution ingest pairing JSON [{}]: {}", instanceName, e.getMessage());
        }
    }

    private static boolean hasUsablePairingMaterial(EvolutionPairingOutcomeDTO o) {
        if (o == null || o.isAlreadyConnected()) {
            return false;
        }
        return (o.getQrCodeDataUri() != null && !o.getQrCodeDataUri().isBlank())
            || (o.getPairingCode() != null && !o.getPairingCode().isBlank());
    }

    @Transactional(readOnly = true)
    public EvolutionPairingOutcomeDTO invokeInstanceConnect(Long usuarioId) {
        PairingCredCacheEntry cached = pairingCredSnapshot(usuarioId);
        ResolvedEvolutionCred cred = cached.cred;
        if (cred.apiKeyHeader.isBlank()
            || evolutionUrl == null || evolutionUrl.isBlank()) {
            return warnOnly(cred.instanceName, "Evolution API não configurada (evolution.url / evolution.apikey)");
        }

        CachedInstancePairing cachedQr = recentPairingByInstance.get(cred.instanceName);
        if (cachedQr != null && cachedQr.fresh() && hasUsablePairingMaterial(cachedQr.outcome)) {
            return cachedQr.outcome;
        }

        boolean sessionSuppressed = evolutionWaSessionRegistry.isUserDisconnected(usuarioId);
        if (!sessionSuppressed && isRealWaSessionOpen(cred)) {
            log.info("Evolution [{}] sessão WA activa (sem novo QR)", cred.instanceName);
            return EvolutionPairingOutcomeDTO.builder()
                .resolvedInstanceName(cred.instanceName)
                .alreadyConnected(true)
                .hasAlternativePairingHints(false)
                .build();
        }
        if (sessionSuppressed || isGhostOpenStaleInstance(cred)) {
            log.info(
                "Evolution [{}] a pedir QR (suppressed={} ghostOpen={})",
                cred.instanceName, sessionSuppressed, isGhostOpenStaleInstance(cred));
        }

        try {
            String urlBase = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/connect/" + cred.instanceName);
            String url = appendNumberQueryIfPresent(urlBase, cached.whatsappDigits);
            int retries = Math.max(1, Math.min(connectRetries, 12));
            if (sessionSuppressed) {
                retries = Math.max(retries, 8);
            }
            for (int attempt = 0; attempt < retries; attempt++) {
                boolean lastAttempt = attempt == retries - 1;
                String body = evolutionGet(url, cred.apiKeyHeader);
                EvolutionPairingOutcomeDTO outcome = parseConnectHttpBody(
                    body, cred.instanceName, lastAttempt, sessionSuppressed);
                if (outcome == null && !lastAttempt) {
                    String postBody = evolutionPostConnect(url, cred.apiKeyHeader);
                    outcome = parseConnectHttpBody(
                        postBody, cred.instanceName, lastAttempt, sessionSuppressed);
                }
                if (outcome == null && (body == null || body.isBlank())) {
                    if (lastAttempt) {
                        return warnOnly(cred.instanceName, "Resposta vazia de /instance/connect" + pairingServerUrlHint());
                    }
                    pauseMillis(safePause());
                    continue;
                }
                if (outcome != null) {
                    if (hasUsablePairingMaterial(outcome)) {
                        recentPairingByInstance.put(
                            cred.instanceName,
                            new CachedInstancePairing(outcome, System.currentTimeMillis() + 120_000L)
                        );
                    }
                    maybeLogWeakPairingResponse(cred.instanceName, attempt, retries, body, outcome);
                    return outcome;
                }
                if (!lastAttempt) {
                    log.debug(
                        "/instance/connect ainda sem QR pareado (instancia={}); nova tentativa após espera ({}/{})",
                        cred.instanceName, attempt + 1, retries - 1);
                    pauseMillis(safePause());
                }
            }
            return warnOnly(
                cred.instanceName,
                "Não foi possível obter o QR pela REST da Evolution após várias tentativas. "
                    + "Abra o Manager da Evolution"
                    + (getManagerUrlForInstance(cred.instanceName).isBlank()
                        ? ""
                        : " (" + getManagerUrlForInstance(cred.instanceName) + ")")
                    + " ou corrija EVOLUTION_SERVER_URL."
                    + pairingServerUrlHint());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String msg = summarizeHttpError(e.getRawStatusCode(),
                e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : e.getMessage());
            log.warn("Evolution /instance/connect falhou [{}]: {}", cred.instanceName, msg);
            return warnOnly(cred.instanceName, msg);
        } catch (Exception ex) {
            log.warn("Evolution /instance/connect erro [{}]: {}", cred.instanceName, ex.getMessage());
            return warnOnly(cred.instanceName, ex.getMessage() != null ? ex.getMessage() : "Erro Evolution");
        }
    }

    @Transactional(readOnly = true)
    public boolean isInstanceConnectedForUser(Long usuarioId) {
        if (evolutionWaSessionRegistry.isUserDisconnected(usuarioId)) {
            return false;
        }
        ResolvedEvolutionCred cred = pairingCredSnapshot(usuarioId).cred;
        return isRealWaSessionOpen(cred);
    }

    /**
     * {@code fetchInstances.connectionStatus} é mais fiável que {@code /connectionState}
     * quando a instância ficou em «open» fantasma após logout (ex. ce-u1).
     */
    @Transactional(readOnly = true)
    public boolean isRealWaSessionOpen(ResolvedEvolutionCred cred) {
        if (cred == null || cred.instanceName == null || cred.instanceName.isBlank()) {
            return false;
        }
        Optional<String> listed = fetchInstancesConnectionStatus(cred.instanceName);
        if (listed.isPresent()) {
            if (isListedAsDisconnected(listed.get())) {
                return false;
            }
            if ("open".equalsIgnoreCase(listed.get().trim())) {
                return true;
            }
        }
        return fetchConnectionStateRaw(cred).filter(this::interpretAsWaConnected).isPresent();
    }

    public boolean isGhostOpenStaleInstance(ResolvedEvolutionCred cred) {
        if (cred == null || cred.instanceName == null || cred.instanceName.isBlank()) {
            return false;
        }
        Optional<String> listed = fetchInstancesConnectionStatus(cred.instanceName);
        if (listed.isEmpty() || !isListedAsDisconnected(listed.get())) {
            return false;
        }
        return fetchConnectionStateRaw(cred).filter(this::interpretAsWaConnected).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<String> fetchInstancesConnectionStatus(String instanceName) {
        if (evolutionUrl == null || evolutionUrl.isBlank()
            || evolutionApiKey == null || evolutionApiKey.isBlank()
            || instanceName == null || instanceName.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/fetchInstances");
            String body = evolutionGet(url, evolutionApiKey.trim());
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                return Optional.empty();
            }
            for (JsonNode item : root) {
                if (item != null && instanceName.equals(item.path("name").asText("").trim())) {
                    String st = item.path("connectionStatus").asText("").trim();
                    return st.isBlank() ? Optional.empty() : Optional.of(st);
                }
            }
        } catch (Exception e) {
            log.debug("fetchInstances connectionStatus [{}]: {}", instanceName, e.getMessage());
        }
        return Optional.empty();
    }

    private static boolean isListedAsDisconnected(String connectionStatus) {
        if (connectionStatus == null || connectionStatus.isBlank()) {
            return true;
        }
        String s = connectionStatus.trim().toLowerCase(Locale.ROOT);
        return s.equals("close")
            || s.equals("closed")
            || s.equals("disconnected")
            || s.equals("disconnect")
            || s.equals("refused")
            || s.equals("logout");
    }

    public void markWaSessionDisconnectedByUser(Long usuarioId) {
        evolutionWaSessionRegistry.markUserDisconnected(usuarioId);
        invalidatePairingCredCache(usuarioId);
    }

    public void clearWaSessionDisconnectedByUser(Long usuarioId) {
        evolutionWaSessionRegistry.clearUserDisconnected(usuarioId);
        invalidatePairingCredCache(usuarioId);
    }

    public boolean isWaSessionDisconnectedByUser(Long usuarioId) {
        return evolutionWaSessionRegistry.isUserDisconnected(usuarioId);
    }

    /** Estado bruto devolvido por {@code GET /instance/connectionState/{instance}} (para UI/diagnóstico). */
    @Transactional(readOnly = true)
    public Optional<String> fetchConnectionStateForUser(Long usuarioId) {
        invalidatePairingCredCache(usuarioId);
        ResolvedEvolutionCred cred = pairingCredSnapshot(usuarioId).cred;
        return fetchConnectionStateRaw(cred);
    }

    @Transactional(readOnly = true)
    public String resolvedInstanceDisplayName(Long usuarioId) {
        return pairingCredSnapshot(usuarioId).cred.instanceName;
    }

    /** Credenciais + opcional dígitos do WhatsApp para query {@code ?number=} (cache TTL curto contra polling). */
    private static final class PairingCredCacheEntry {
        final ResolvedEvolutionCred cred;
        final Optional<String> whatsappDigits;
        final long expiresAtEpochMs;

        PairingCredCacheEntry(ResolvedEvolutionCred cred, Optional<String> whatsappDigits, long expiresAtEpochMs) {
            this.cred = cred;
            this.whatsappDigits = whatsappDigits;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }

        boolean fresh(long nowMs) {
            return nowMs < expiresAtEpochMs;
        }
    }

    private PairingCredCacheEntry pairingCredSnapshot(Long usuarioId) {
        long now = System.currentTimeMillis();
        if (usuarioId == null) {
            return new PairingCredCacheEntry(loadResolvedEvolutionCred(null), loadWhatsappDigits(null), Long.MAX_VALUE);
        }
        PairingCredCacheEntry prev = pairingCredCache.get(usuarioId);
        if (prev != null && prev.fresh(now)) {
            return prev;
        }
        long expiry = now + Math.max(1_000L, pairingCredCacheMs);
        PairingCredCacheEntry entry = new PairingCredCacheEntry(
            loadResolvedEvolutionCred(usuarioId), loadWhatsappDigits(usuarioId), expiry);
        pairingCredCache.put(usuarioId, entry);
        return entry;
    }

    @Transactional(readOnly = true)
    public ResolvedEvolutionCred resolveCredentials(Long usuarioId) {
        return pairingCredSnapshot(usuarioId).cred;
    }

    private ResolvedEvolutionCred loadResolvedEvolutionCred(Long usuarioId) {
        String inst = (defaultEvolutionInstance != null && !defaultEvolutionInstance.trim().isEmpty())
            ? defaultEvolutionInstance.trim()
            : "ConsumoEsperto";
        String api = evolutionApiKey != null ? evolutionApiKey.trim() : "";

        if (usuarioId != null) {
            Optional<UsuarioAiConfig> cfgOpt = usuarioAiConfigRepository.findByUsuarioId(usuarioId);
            if (cfgOpt.isPresent()) {
                UsuarioAiConfig c = cfgOpt.get();
                if (c.getEvolutionInstanceName() != null && !c.getEvolutionInstanceName().isBlank()) {
                    inst = c.getEvolutionInstanceName().trim();
                }
                if (c.getEvolutionApiKey() != null && !c.getEvolutionApiKey().isBlank()) {
                    api = c.getEvolutionApiKey().trim();
                }
            }
        }
        return new ResolvedEvolutionCred(inst, api);
    }

    private Optional<String> loadWhatsappDigits(Long usuarioId) {
        if (usuarioId == null) {
            return Optional.empty();
        }
        return usuarioRepository.findById(usuarioId)
            .map(Usuario::getWhatsappNumero)
            .map(raw -> raw == null ? "" : raw.replaceAll("\\D+", ""))
            .filter(d -> !d.isBlank() && d.length() >= 10 && d.length() <= 17);
    }

    private Optional<String> fetchConnectionStateRaw(ResolvedEvolutionCred cred) {
        if (evolutionUrl == null || evolutionUrl.isBlank() || cred.apiKeyHeader.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/connectionState/" + cred.instanceName);
            String body = evolutionGet(url, cred.apiKeyHeader);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = unwrapTopLevelEvolutionEnvelope(objectMapper.readTree(body));
            return extractStateFromPayload(root);
        } catch (Exception e) {
            log.debug("connectionState opcional falhou [{}]: {}", cred.instanceName, e.getMessage());
            return Optional.empty();
        }
    }

    private String evolutionGet(String url, String apiKey) {
        return evolutionHttp(url, apiKey, HttpMethod.GET);
    }

    private String evolutionPostConnect(String url, String apiKey) {
        return evolutionHttp(url, apiKey, HttpMethod.POST);
    }

    private String evolutionHttp(String url, String apiKey, HttpMethod method) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("apikey", apiKey.trim());
                headers.setBearerAuth(apiKey.trim());
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            return resp.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.debug("Evolution {} {}: HTTP {}", method, url, e.getRawStatusCode());
            return e.getResponseBodyAsString();
        } catch (Exception ex) {
            log.debug("Evolution {} {}: {}", method, url, ex.getMessage());
            return null;
        }
    }

    private EvolutionPairingOutcomeDTO parseConnectHttpBody(
        String body, String instanceName, boolean lastAttempt, boolean sessionSuppressed
    ) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = unwrapTopLevelEvolutionEnvelope(objectMapper.readTree(body));
            return processConnectBody(root, instanceName, lastAttempt, sessionSuppressed);
        } catch (Exception e) {
            log.debug("Evolution parse connect [{}]: {}", instanceName, e.getMessage());
            return lastAttempt ? warnOnly(instanceName, "JSON inválido da Evolution: " + e.getMessage()) : null;
        }
    }

    private String pairingServerUrlHint() {
        String pub = evolutionPublicUrl != null ? evolutionPublicUrl.trim() : "";
        if (pub.isBlank()) {
            return " Defina EVOLUTION_SERVER_URL no .env (URL pública da Evolution, ex. https://dominio:8585) "
                + "e reinicie o contentor evolution_api.";
        }
        return " Confira EVOLUTION_SERVER_URL=" + pub + " no contentor evolution_api (deve ser a URL da Evolution, não do frontend).";
    }

    /**
     * Interpreta JSON de {@code /instance/connect}. Devolve {@code null} quando convém novo GET após espera curta (QR ainda não preenchido na Evolution).
     */
    private EvolutionPairingOutcomeDTO processConnectBody(
        JsonNode root, String instanceName, boolean lastAttempt, boolean sessionSuppressed
    ) {
        if (root == null || root.isNull()) {
            return lastAttempt ? warnOnly(instanceName, "Resposta JSON invalida da Evolution.") : null;
        }

        if (isEvolutionJsonError(root)) {
            String msg = firstNonBlank(nodeText(root, "message"));
            return warnOnly(instanceName, msg.isBlank() ? "Evolution devolveu erro" : msg);
        }

        JsonNode countNode = root.get("count");
        if (countNode != null && countNode.isNumber() && countNode.asInt() == 0 && !lastAttempt) {
            log.debug("Evolution [{}] connect count=0 — nova tentativa (SERVER_URL / sessão)", instanceName);
            return null;
        }

        List<JsonNode> layers = collectConnectLayers(root);

        Optional<String> stAfter = Optional.empty();
        for (JsonNode layer : layers) {
            Optional<String> s = extractStateFromPayload(layer);
            if (s.isPresent()) {
                stAfter = s;
                break;
            }
        }
        if (stAfter.isPresent() && interpretAsWaConnected(stAfter.get()) && !sessionSuppressed) {
            Optional<String> listed = fetchInstancesConnectionStatus(instanceName);
            boolean ghostOpen = listed.filter(EvolutionPairingService::isListedAsDisconnected).isPresent();
            if (ghostOpen && !lastAttempt) {
                log.debug("Evolution [{}] connect open mas fetchInstances=close — nova tentativa", instanceName);
                return null;
            }
            if (!ghostOpen) {
                return EvolutionPairingOutcomeDTO.builder()
                    .resolvedInstanceName(instanceName)
                    .alreadyConnected(true)
                    .hasAlternativePairingHints(false)
                    .build();
            }
        }

        String pairing = "";
        for (JsonNode layer : layers) {
            pairing = firstNonBlank(pairing, extractPairingFields(layer));
        }
        Optional<String> connectCode = Optional.empty();
        for (JsonNode layer : layers) {
            connectCode = connectCode.or(() -> firstMeaningfulCodeOne(layer));
        }
        // Preferir QR preto gerado localmente a partir do campo "code" — a Evolution pinta o PNG com QRCODE_COLOR (ex. verde).
        Optional<String> qrUri = connectCode.flatMap(this::qrPngFromString);
        if (qrUri.isEmpty()) {
            qrUri = resolveQrAcrossLayers(layers);
        }

        EvolutionPairingOutcomeDTO.EvolutionPairingOutcomeDTOBuilder b = EvolutionPairingOutcomeDTO.builder()
            .resolvedInstanceName(instanceName)
            .pairingCode(pairing.isBlank() ? null : pairing)
            .alreadyConnected(false);

        if (qrUri.isPresent()) {
            return b.qrCodeDataUri(qrUri.get())
                .hasAlternativePairingHints(!pairing.isBlank())
                .build();
        }

        if (!pairing.isBlank()) {
            return b.hasAlternativePairingHints(true).build();
        }

        if (!lastAttempt && evolutionMayStillProduceQr(root, layers, sessionSuppressed)) {
            return null;
        }

        boolean countZero = countNode != null && countNode.isNumber() && countNode.asInt() == 0;
        String warn = connectCode.isPresent()
            ? "Evolution não devolveu PNG base64 nem pairingCode utilizável pela REST neste momento "
                + "(o código interno WhatsApp existe mas não pode ser convertido a QR pelo servidor). Veja o Manager ou websocket."
            : countZero
                ? "Evolution respondeu {count:0} sem QR — quase sempre EVOLUTION_SERVER_URL incorrecto no contentor evolution_api."
                : "Evolution não devolveu dados de pairing por REST após tentativas repetidas — veja QR no Manager da Evolution.";
        warn += pairingServerUrlHint();
        String mgr = getManagerUrlForInstance(instanceName);
        if (!mgr.isBlank()) {
            warn += " Manager: " + mgr;
        }
        return EvolutionPairingOutcomeDTO.builder()
            .resolvedInstanceName(instanceName)
            .alreadyConnected(false)
            .evolutionWarning(warn)
            .hasAlternativePairingHints(false)
            .build();
    }

    /** {@code GET /instance/connect?number=} melhora pairing por número quando a Evolution aceita esse query param. */
    private static String appendNumberQueryIfPresent(String baseUrl, Optional<String> digitsOpt) {
        if (baseUrl == null || baseUrl.isBlank() || digitsOpt == null || digitsOpt.isEmpty()) {
            return baseUrl;
        }
        return UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("number", digitsOpt.get())
            .build(true)
            .toUriString();
    }

    /** Evolution às vezes devolve lista de um elemento ou objeto com {@code data} array — normaliza para objeto. */
    private static JsonNode unwrapTopLevelEvolutionEnvelope(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isArray()) {
            if (node.size() == 1) {
                return unwrapTopLevelEvolutionEnvelope(node.get(0));
            }
            return node;
        }
        if (node.isObject()) {
            JsonNode data = node.get("data");
            if (data != null && !data.isNull() && data.isArray() && data.size() >= 1) {
                JsonNode inner = unwrapTopLevelEvolutionEnvelope(data.get(0));
                if (inner != null && inner.isObject()) {
                    return inner;
                }
            }
        }
        return node;
    }

    private void maybeLogWeakPairingResponse(String instanceName, int attempt, int retries,
        String body, EvolutionPairingOutcomeDTO outcome) {
        if (!logWeakEvolutionResponses || outcome == null) {
            return;
        }
        if (outcome.isAlreadyConnected()) {
            return;
        }
        String qr = outcome.getQrCodeDataUri();
        String pairing = outcome.getPairingCode();
        boolean usable = (qr != null && !qr.isBlank()) || (pairing != null && !pairing.isBlank());
        if (usable) {
            return;
        }
        String warn = outcome.getEvolutionWarning();
        if (warn == null || warn.isBlank()) {
            return;
        }
        log.warn("Evolution resposta \"fraca\" [{}] tentativa {}/{}: {} :: corpo={}",
            instanceName, attempt + 1, retries, warn, sanitizeEvolutionRestBodySnippet(body));
    }

    private static String sanitizeEvolutionRestBodySnippet(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(vazio)";
        }
        String s = raw.replaceAll("[a-zA-Z0-9+/]{120,}=?", "[base64…]");
        int max = 800;
        if (s.length() > max) {
            return s.substring(0, max) + "\u2026";
        }
        return s;
    }

    private long safePause() {
        long ms = Math.max(500L, Math.min(connectPauseMs > 0 ? connectPauseMs : 4000L, 15000L));
        return ms;
    }

    private static void pauseMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode unwrapDataLayer(JsonNode root) {
        if (root != null && root.isObject()) {
            JsonNode data = root.get("data");
            if (data != null && !data.isMissingNode()) {
                return data.isObject() || data.isArray() ? data : root;
            }
        }
        return root;
    }

    /** Objectos onde a EvoAPI pode colocar base64/code/pairing (root, {@code data}, {@code response}, {@code instance}, {@code qrcode}). */
    private List<JsonNode> collectConnectLayers(JsonNode root) {
        List<JsonNode> layers = new ArrayList<>();
        if (root == null || root.isNull()) {
            return layers;
        }
        layers.add(root);
        JsonNode unwrappedData = unwrapDataLayer(root);
        if (unwrappedData != null && !unwrappedData.equals(root)) {
            layers.add(unwrappedData);
        }
        if (root.isObject()) {
            addJsonObjectOrArrayElements(layers, root.get("response"));
            JsonNode inst = root.get("instance");
            if (inst != null && inst.isObject()) {
                layers.add(inst);
            }
            JsonNode qrcode = root.get("qrcode");
            if (qrcode != null && qrcode.isObject()) {
                layers.add(qrcode);
            }
        }
        return layers;
    }

    private static void addJsonObjectOrArrayElements(List<JsonNode> layers, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            layers.add(node);
            return;
        }
        if (node.isArray()) {
            for (JsonNode elt : node) {
                if (elt != null && elt.isObject()) {
                    layers.add(elt);
                }
            }
        }
    }

    private Optional<String> resolveQrAcrossLayers(List<JsonNode> layers) {
        if (layers == null) {
            return Optional.empty();
        }
        for (JsonNode layer : layers) {
            Optional<String> one = resolveQrAsDataUri(layer);
            if (one.isPresent()) {
                return one;
            }
        }
        return Optional.empty();
    }

    /** Algumas versões usam erro boolean ou string "BadRequestException: …". */
    private static boolean isEvolutionJsonError(JsonNode root) {
        JsonNode err = root == null ? null : root.get("error");
        if (err == null || err.isNull()) {
            return false;
        }
        if (err.isBoolean()) {
            return err.asBoolean(false);
        }
        if (err.isTextual()) {
            String t = err.asText("").trim();
            return "true".equalsIgnoreCase(t) || t.toUpperCase(Locale.ROOT).startsWith("BADREQUEST")
                || t.toUpperCase(Locale.ROOT).startsWith("ERROR");
        }
        return false;
    }

    /** Campos de pairing num único objeto JSON (sem desembrulhar <code>data</code>). */
    private String extractPairingFields(JsonNode r) {
        if (r == null || r.isMissingNode()) {
            return "";
        }
        return firstNonBlank(
            nodeText(r, "pairingCode"),
            nodeText(r, "pairing_code"),
            nestedAsText(r.path("qrCode"), "pairingCode"),
            nestedAsText(r.path("qr_code"), "pairing_code"),
            nodeText(r.path("qrcode"), "pairingCode"),
            nestedAsText(r.path("qrcode"), "pairingCode"),
            nestedAsText(r.path("qrcode"), "pairing_code"),
            nodeText(r.path("instance").path("qrcode"), "pairingCode"),
            nodeText(r.path("instance").path("qrcode"), "pairing_code")
        );
    }

    /**
     * Mantém vários GETs espaçados quando a Evolution ainda pode preencher QR/pairing.
     */
    private boolean evolutionMayStillProduceQr(JsonNode root, List<JsonNode> layers, boolean sessionSuppressed) {
        if (isEvolutionJsonError(root)) {
            return false;
        }
        if (layers == null) {
            return true;
        }
        for (JsonNode layer : layers) {
            if (!extractPairingFields(layer).isBlank()) {
                return false;
            }
        }
        if (resolveQrAcrossLayers(layers).isPresent()) {
            return false;
        }
        for (JsonNode layer : layers) {
            Optional<String> stOpt = extractStateFromPayload(layer);
            if (stOpt.isPresent() && interpretAsWaConnected(stOpt.get()) && !sessionSuppressed) {
                return false;
            }
        }
        return true;
    }

    private Optional<String> extractStateFromPayload(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return Optional.empty();
        }
        String s = firstNonBlank(
            nodeText(root.path("instance"), "state"),
            nodeText(root.path("connection"), "state"),
            nodeText(root, "state"),
            nodeText(root.path("instance"), "status"),
            nodeText(root, "connectionState")
        );
        return Optional.ofNullable(trimToNull(s));
    }

    private Optional<String> resolveQrAsDataUri(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Optional.empty();
        }
        String blob = pickFirstQrBase64Field(node);
        if (blob != null && !blob.isBlank()) {
            return Optional.of(normalizeRasterToDataUri(blob));
        }
        return Optional.empty();
    }

    private String pickFirstQrBase64Field(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "";
        }
        /* Nunca usar o campo Baileys "code" como base64 — não é PNG; causa Data URI corrupto ou falha UX. */
        return firstNonBlank(
            nestedAsText(root, "base64"),
            nestedAsText(root, "pngBase64"),
            nestedAsText(root.path("qrcode"), "base64"),
            nestedAsText(root.path("qrcode"), "pngBase64"),
            nestedAsText(root.path("qr"), "base64"),
            nodeText(root, "qr"),
            nodeText(root, "qrCode"),
            nodeText(root, "qr_base64"),
            nodeText(root, "QRCode"),
            nodeText(root.path("data"), "base64"),
            nodeText(root.path("result"), "base64"),
            nestedAsText(root.path("instance").path("qrcode"), "base64"),
            nestedAsText(root.path("response"), "base64"),
            nestedAsText(root.path("response").path("qrcode"), "base64")
        );
    }

    /** Texto só se parecer texto (Evita ler JSON objeto como código). */
    private static String nestedAsText(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return "";
        }
        JsonNode node = parent.get(field);
        if (node == null || node.isMissingNode() || node.isArray() || node.isObject()) {
            return "";
        }
        return node.asText("").trim();
    }

    private Optional<String> firstMeaningfulCodeOne(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return Optional.empty();
        }
        String c = nodeText(root, "code");
        if (!c.isBlank()) {
            return Optional.of(c);
        }
        c = nestedAsText(root.path("qrcode"), "code");
        if (!c.isBlank()) {
            return Optional.of(c);
        }
        c = nestedAsText(root.path("instance").path("qrcode"), "code");
        if (!c.isBlank()) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    /** Tradução permissiva das variantes vistas em Baileys / Evolution docs. */
    /**
     * Só estados explícitos de sessão WA activa — evita falso positivo (ex. substring em "connecting")
     * e alinha com utilizador que desligou o WhatsApp no telemóvel mas a BD ainda tem número.
     */
    private boolean interpretAsWaConnected(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        if (s.contains("disconnect")
            || s.contains("logout")
            || s.contains("unpair")
            || s.contains("close")
            || s.contains("refused")
            || s.contains("qrcode")
            || s.equals("pairing")
            || s.equals("connecting")) {
            return false;
        }
        return s.equals("open")
            || s.equals("connected")
            || s.equals("success")
            || s.equals("online");
    }

    private static String nodeText(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        return node.path(field).asText("").trim();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return "";
        }
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String trimToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }

    private static EvolutionPairingOutcomeDTO warnOnly(String instance, String warning) {
        return EvolutionPairingOutcomeDTO.builder()
            .resolvedInstanceName(instance)
            .evolutionWarning(warning != null ? warning : "Erro Evolution")
            .alreadyConnected(false)
            .hasAlternativePairingHints(false)
            .build();
    }

    private static String summarizeHttpError(int status, String bodySnippet) {
        String shortBody = abbreviate(bodySnippet, 260);
        return "HTTP " + status + (shortBody.isBlank() ? "" : (": " + shortBody));
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static String normalizeRasterToDataUri(String rawBase64OrDataUri) {
        String s = rawBase64OrDataUri.trim().replace("\n", "").replace("\r", "");
        if (s.startsWith("data:")) {
            return s;
        }
        try {
            int take = Math.min(s.length(), 256);
            String slice = s.substring(0, take);
            byte[] decoded = decodeBase64WithPadding(slice);
            if (decoded != null && decoded.length >= 3
                && decoded[0] == (byte) 0xFF && decoded[1] == (byte) 0xD8 && decoded[2] == (byte) 0xFF) {
                return "data:image/jpeg;base64," + s;
            }
        } catch (Exception ignored) {
            /* assume PNG por defeito */
        }
        return "data:image/png;base64," + s;
    }

    private static byte[] decodeBase64WithPadding(String base64Snippet) {
        try {
            String padded = base64Snippet;
            while (padded.length() % 4 != 0) {
                padded += "=";
            }
            return java.util.Base64.getDecoder().decode(padded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Optional<String> qrPngFromString(String whatsappQrPayload) {
        try {
            int size = 512;
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix bm = new MultiFormatWriter().encode(whatsappQrPayload, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bm, "PNG", os);
            String enc = java.util.Base64.getEncoder().encodeToString(os.toByteArray());
            return Optional.of("data:image/png;base64," + enc);
        } catch (Exception e) {
            log.debug("Gerar PNG a partir do campo \"code\" da Evolution falhou: {}", e.getMessage());
            return laxQrAttempt(whatsappQrPayload);
        }
    }

    private Optional<String> laxQrAttempt(String payload) {
        try {
            BitMatrix bm = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512, null);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bm, "PNG", os);
            String enc = java.util.Base64.getEncoder().encodeToString(os.toByteArray());
            return Optional.of("data:image/png;base64," + enc);
        } catch (Exception e2) {
            return Optional.empty();
        }
    }

    /** Credenciais resolvidas para uma chamada HTTP (uso interno e testes). */
    public static final class ResolvedEvolutionCred {
        public final String instanceName;
        public final String apiKeyHeader;

        ResolvedEvolutionCred(String instanceName, String apiKeyHeader) {
            this.instanceName = instanceName;
            this.apiKeyHeader = apiKeyHeader != null ? apiKeyHeader.trim() : "";
        }
    }
}
