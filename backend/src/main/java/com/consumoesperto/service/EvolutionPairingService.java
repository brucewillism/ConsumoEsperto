package com.consumoesperto.service;

import com.consumoesperto.dto.EvolutionPairingOutcomeDTO;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
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

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pareamento WhatsApp na Evolution API (QR / código) — usando {@code evolution.url}, {@code evolution.apikey}
 * e opcionalmente instância e chave próprias do utilizador ({@link UsuarioAiConfig}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvolutionPairingService {

    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:ConsumoEsperto}")
    private String defaultEvolutionInstance;

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
    public EvolutionPairingOutcomeDTO invokeInstanceConnect(Long usuarioId) {
        ResolvedEvolutionCred cred = resolveCredentials(usuarioId);
        if (cred.apiKeyHeader.isBlank()
            || evolutionUrl == null || evolutionUrl.isBlank()) {
            return warnOnly(cred.instanceName, "Evolution API não configurada (evolution.url / evolution.apikey)");
        }

        Optional<String> preState = fetchConnectionStateRaw(cred);
        if (preState.isPresent() && interpretAsWaConnected(preState.get())) {
            return EvolutionPairingOutcomeDTO.builder()
                .resolvedInstanceName(cred.instanceName)
                .alreadyConnected(true)
                .hasAlternativePairingHints(false)
                .build();
        }

        try {
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "instance/connect/" + cred.instanceName);
            String body = evolutionGet(url, cred.apiKeyHeader);
            if (body == null || body.isBlank()) {
                return warnOnly(cred.instanceName, "Resposta vazia de /instance/connect");
            }
            JsonNode root = objectMapper.readTree(body);
            Optional<String> stAfter = extractStateFromPayload(root);
            if (stAfter.isPresent() && interpretAsWaConnected(stAfter.get())) {
                return EvolutionPairingOutcomeDTO.builder()
                    .resolvedInstanceName(cred.instanceName)
                    .alreadyConnected(true)
                    .hasAlternativePairingHints(false)
                    .build();
            }

            Optional<String> qrUri = resolveQrAsDataUri(root);
            String pairing = firstNonBlank(
                nodeText(root, "pairingCode"),
                nodeText(root.path("qrcode"), "pairingCode")
            );

            EvolutionPairingOutcomeDTO.EvolutionPairingOutcomeDTOBuilder b = EvolutionPairingOutcomeDTO.builder()
                .resolvedInstanceName(cred.instanceName)
                .pairingCode(pairing.isBlank() ? null : pairing)
                .alreadyConnected(false);

            if (qrUri.isPresent()) {
                return b.qrCodeDataUri(qrUri.get())
                    .hasAlternativePairingHints(!pairing.isBlank())
                    .build();
            }

            Optional<String> connectCode = firstMeaningfulCode(root);
            if (connectCode.isPresent()) {
                Optional<String> generated = qrPngFromString(connectCode.get());
                if (generated.isPresent()) {
                    return b.qrCodeDataUri(generated.get())
                        .hasAlternativePairingHints(!pairing.isBlank())
                        .build();
                }
            }

            boolean hints = !pairing.isBlank();
            return b.evolutionWarning(
                    "Evolution não devolveu QR reconhecível (REST). Veja pairingCode ou o QR no Manager da Evolution.")
                .hasAlternativePairingHints(hints)
                .build();
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

    public boolean isInstanceConnectedForUser(Long usuarioId) {
        ResolvedEvolutionCred cred = resolveCredentials(usuarioId);
        return fetchConnectionStateRaw(cred)
            .filter(this::interpretAsWaConnected)
            .isPresent();
    }

    public String resolvedInstanceDisplayName(Long usuarioId) {
        return resolveCredentials(usuarioId).instanceName;
    }

    @Transactional(readOnly = true)
    public ResolvedEvolutionCred resolveCredentials(Long usuarioId) {
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
            JsonNode root = objectMapper.readTree(body);
            return extractStateFromPayload(root);
        } catch (Exception e) {
            log.debug("connectionState opcional falhou [{}]: {}", cred.instanceName, e.getMessage());
            return Optional.empty();
        }
    }

    private String evolutionGet(String url, String apiKey) {
        if (url == null || url.isBlank()) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("apikey", apiKey);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return resp.getBody();
    }

    private Optional<String> extractStateFromPayload(JsonNode root) {
        String s = firstNonBlank(
            nodeText(root.path("instance"), "state"),
            nodeText(root, "state"),
            nodeText(root.path("instance"), "status"),
            nodeText(root, "connectionState")
        );
        return Optional.ofNullable(trimToNull(s));
    }

    private Optional<String> resolveQrAsDataUri(JsonNode root) {
        String blob = pickFirstQrBase64Field(root);
        if (blob == null || blob.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(normalizeRasterToDataUri(blob));
    }

    private String pickFirstQrBase64Field(JsonNode root) {
        return firstNonBlank(
            nestedAsText(root, "base64"),
            nestedAsText(root.path("qrcode"), "base64"),
            nestedAsText(root.path("qrcode"), "code"),
            nestedAsText(root.path("qr"), "base64"),
            nodeText(root, "qr"),
            nodeText(root, "qrCode"),
            nodeText(root, "QRCode"),
            nodeText(root.path("data"), "base64"),
            nodeText(root.path("result"), "base64"),
            nestedAsText(root.path("instance").path("qrcode"), "base64"),
            nestedAsText(root.path("instance").path("qrcode"), "code"),
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

    private Optional<String> firstMeaningfulCode(JsonNode root) {
        String c = nodeText(root, "code");
        if (!c.isBlank()) {
            return Optional.of(c);
        }
        c = nodeText(root.path("qrcode"), "code");
        if (!c.isBlank()) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    /** Tradução permissiva das variantes vistas em Baileys / Evolution docs. */
    private boolean interpretAsWaConnected(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String compact = state.trim().toUpperCase(Locale.ROOT);
        if (compact.contains("DISCONNECT")) {
            return false;
        }
        if (compact.equals("CLOSE") || compact.equals("CLOSED")) {
            return false;
        }
        return compact.contains("CONNECTED")
            || compact.equals("OPEN")
            || compact.equals("SUCCESS");
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
