package com.consumoesperto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EvolutionMediaService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    public byte[] fetchMedia(String mediaUrl) {
        return fetchMedia(mediaUrl, null);
    }

    /**
     * @param apiKeyOverride quando não nulo/vazio, substitui {@code evolution.apikey} (ex.: chave por utilizador).
     */
    public byte[] fetchMedia(String mediaUrl, String apiKeyOverride) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }
        String key = (apiKeyOverride != null && !apiKeyOverride.isBlank()) ? apiKeyOverride.trim() : evolutionApiKey;
        HttpHeaders headers = new HttpHeaders();
        if (key != null && !key.isBlank()) {
            headers.set("apikey", key);
        }
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(mediaUrl, HttpMethod.GET, req, byte[].class);
        return resp.getBody();
    }

    /**
     * Obtém bytes reais da mídia via Evolution (desencriptado). URLs {@code mmg.whatsapp.net} com GET direto
     * costumam não ser áudio válido para o Groq.
     */
    public byte[] fetchBase64FromMediaMessage(String instanceName, String remoteJid, String messageId, boolean fromMe) {
        return fetchBase64FromMediaMessage(instanceName, remoteJid, messageId, fromMe, null);
    }

    public byte[] fetchBase64FromMediaMessage(String instanceName, String remoteJid, String messageId, boolean fromMe, String apiKeyOverride) {
        if (evolutionUrl == null || evolutionUrl.isBlank()
            || instanceName == null || instanceName.isBlank()
            || remoteJid == null || remoteJid.isBlank()
            || messageId == null || messageId.isBlank()) {
            return null;
        }
        String key = (apiKeyOverride != null && !apiKeyOverride.isBlank()) ? apiKeyOverride.trim() : evolutionApiKey;
        String base = evolutionUrl.endsWith("/") ? evolutionUrl.substring(0, evolutionUrl.length() - 1) : evolutionUrl;
        String url = base + "/chat/getBase64FromMediaMessage/" + instanceName.trim();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (key != null && !key.isBlank()) {
                headers.set("apikey", key);
            }
            Map<String, Object> waMessageKey = new LinkedHashMap<>();
            waMessageKey.put("remoteJid", remoteJid);
            waMessageKey.put("fromMe", fromMe);
            waMessageKey.put("id", messageId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", Map.of("key", waMessageKey));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("Evolution getBase64FromMediaMessage HTTP {} corpo vazio ou erro", resp.getStatusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            String b64 = firstNonBlank(
                textAt(root, "base64"),
                textAt(root.path("media"), "base64"),
                textAt(root.path("data"), "base64")
            );
            if (b64 == null || b64.isBlank()) {
                log.warn("Evolution getBase64FromMediaMessage: resposta sem base64: {}", abbreviate(resp.getBody(), 300));
                return null;
            }
            String normalized = b64.contains(",") ? b64.substring(b64.indexOf(',') + 1) : b64;
            return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Falha getBase64FromMediaMessage Evolution (instancia={}): {}", instanceName, e.getMessage());
            return null;
        }
    }

    private static String textAt(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return "";
        }
        return parent.path(field).asText("");
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return c != null && !c.isBlank() ? c : "";
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
