package com.consumoesperto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.consumoesperto.util.EvolutionUrlSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EvolutionApiService {

    private final EvolutionBotEchoFilterService evolutionBotEchoFilterService;
    private final ObjectMapper objectMapper;
    private final EvolutionSessionWatchdogService evolutionSessionWatchdogService;

    private RestTemplate restTemplate;

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:}")
    private String evolutionInstance;

    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(25_000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * @return {@code true} se a Evolution aceitou o envio (2xx/3xx); caso contrário {@code false} (não propaga excepção).
     */
    public boolean enviarMensagem(String to, String message) {
        return enviarMensagem(to, message, null);
    }

    /**
     * @param evolutionInstanceOverride nome da instância na Evolution (multitenant); se vazio usa {@link #evolutionInstance}
     */
    public boolean enviarMensagem(String to, String message, String evolutionInstanceOverride) {
        try {
            ensureApiConfigured();
            ensureDefaultInstanceIfNeeded(evolutionInstanceOverride);
            String number = normalizeToNumber(to);
            if (number.isBlank()) {
                log.error("[EvolutionApi] Envio abortado: número destino inválido. [J.A.R.V.I.S. Offline]");
                return false;
            }

            String instance = resolveInstanceName(evolutionInstanceOverride);

            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "/message/sendText/" + instance);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", evolutionApiKey);

            Map<String, Object> payload = Map.of(
                "number", number,
                "text", message
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() && !response.getStatusCode().is3xxRedirection()) {
                log.error("[EvolutionApi] Falha HTTP ao enviar texto: {} instance={} destino={} [J.A.R.V.I.S. Offline]",
                    response.getStatusCode(), instance, number);
                evolutionSessionWatchdogService.onSendFailure(instance);
                return false;
            }
            registerOutgoingFromResponse(number, response.getBody());
            return true;
        } catch (IllegalStateException cfg) {
            log.error("[EvolutionApi] Configuração Evolution incompleta: {} [J.A.R.V.I.S. Offline]", cfg.getMessage());
            return false;
        } catch (ResourceAccessException ex) {
            log.error("[EvolutionApi] Evolution offline ou timeout ao enviar mensagem (rede): {} [J.A.R.V.I.S. Offline]", ex.getMessage());
            evolutionSessionWatchdogService.onSendFailure(resolveInstanceName(evolutionInstanceOverride));
            return false;
        } catch (RestClientException ex) {
            log.error("[EvolutionApi] Erro de cliente HTTP Evolution ao enviar mensagem: {} [J.A.R.V.I.S. Offline]", ex.getMessage(), ex);
            evolutionSessionWatchdogService.onSendFailure(resolveInstanceName(evolutionInstanceOverride));
            return false;
        } catch (RuntimeException ex) {
            log.error("[EvolutionApi] Erro ao enviar mensagem pela Evolution: {} [J.A.R.V.I.S. Offline]", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Envia mensagem de voz (PTT) — {@code POST /message/sendWhatsAppAudio/:instance}.
     */
    public boolean enviarWhatsAppAudioPtt(String to, byte[] audioBytes, String evolutionInstanceOverride) {
        try {
            ensureApiConfigured();
            if (audioBytes == null || audioBytes.length == 0) {
                log.error("[EvolutionApi] Áudio PTT abortado: bytes vazios. [J.A.R.V.I.S. Offline]");
                return false;
            }
            String number = normalizeToNumber(to);
            if (number.isBlank()) {
                log.error("[EvolutionApi] Áudio PTT abortado: número inválido. [J.A.R.V.I.S. Offline]");
                return false;
            }
            String instance = resolveInstanceName(evolutionInstanceOverride);
            if (instance == null || instance.isBlank()) {
                log.error("[EvolutionApi] Áudio PTT abortado: instância Evolution ausente. [J.A.R.V.I.S. Offline]");
                return false;
            }
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "/message/sendWhatsAppAudio/" + instance);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", evolutionApiKey);

            String b64 = Base64.getEncoder().encodeToString(audioBytes);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("number", number);
            payload.put("audio", b64);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() && !response.getStatusCode().is3xxRedirection()) {
                log.error("[EvolutionApi] Falha HTTP ao enviar áudio PTT: {} instance={} [J.A.R.V.I.S. Offline]",
                    response.getStatusCode(), instance);
                return false;
            }
            log.info("[JARVIS-LOG] Evolution áudio PTT enviado instance={} destino={}", instance, number);
            registerOutgoingFromResponse(number, response.getBody());
            evolutionBotEchoFilterService.registerOutgoingMedia(number, "audio", audioBytes, null);
            return true;
        } catch (IllegalStateException cfg) {
            log.error("[EvolutionApi] Áudio: configuração incompleta: {} [J.A.R.V.I.S. Offline]", cfg.getMessage());
            return false;
        } catch (ResourceAccessException ex) {
            log.error("[EvolutionApi] Áudio PTT timeout/rede: {} [J.A.R.V.I.S. Offline]", ex.getMessage());
            return false;
        } catch (RestClientException ex) {
            log.error("[EvolutionApi] Erro HTTP áudio PTT: {} [J.A.R.V.I.S. Offline]", ex.getMessage(), ex);
            return false;
        } catch (RuntimeException ex) {
            log.error("[EvolutionApi] Erro áudio PTT: {} [J.A.R.V.I.S. Offline]", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Envia PDF como documento (base64) — {@code /message/sendMedia/:instance}.
     */
    public boolean enviarDocumentoPdf(String to, byte[] pdfBytes, String fileName, String evolutionInstanceOverride) {
        try {
            ensureApiConfigured();
            if (pdfBytes == null || pdfBytes.length == 0) {
                log.error("[EvolutionApi] Envio PDF abortado: bytes vazios. [J.A.R.V.I.S. Offline]");
                return false;
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = "documento.pdf";
            }
            String number = normalizeToNumber(to);
            if (number.isBlank()) {
                log.error("[EvolutionApi] Envio PDF abortado: número inválido. [J.A.R.V.I.S. Offline]");
                return false;
            }
            String instance = resolveInstanceName(evolutionInstanceOverride);
            if (instance == null || instance.isBlank()) {
                log.error("[EvolutionApi] Envio PDF abortado: instância Evolution ausente. [J.A.R.V.I.S. Offline]");
                return false;
            }
            String url = EvolutionUrlSupport.joinEvolutionPath(evolutionUrl, "/message/sendMedia/" + instance);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", evolutionApiKey);

            String b64 = Base64.getEncoder().encodeToString(pdfBytes);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("number", number);
            payload.put("mediatype", "document");
            payload.put("mimetype", "application/pdf");
            payload.put("fileName", fileName);
            payload.put("media", b64);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() && !response.getStatusCode().is3xxRedirection()) {
                log.error("[EvolutionApi] Falha HTTP ao enviar PDF: {} instance={} [J.A.R.V.I.S. Offline]", response.getStatusCode(), instance);
                return false;
            }
            registerOutgoingFromResponse(number, response.getBody());
            evolutionBotEchoFilterService.registerOutgoingMedia(number, "document", pdfBytes, fileName);
            return true;
        } catch (IllegalStateException cfg) {
            log.error("[EvolutionApi] PDF: configuração incompleta: {} [J.A.R.V.I.S. Offline]", cfg.getMessage());
            return false;
        } catch (ResourceAccessException ex) {
            log.error("[EvolutionApi] Evolution offline ou timeout ao enviar PDF: {} [J.A.R.V.I.S. Offline]", ex.getMessage());
            return false;
        } catch (RestClientException ex) {
            log.error("[EvolutionApi] Erro HTTP ao enviar PDF: {} [J.A.R.V.I.S. Offline]", ex.getMessage(), ex);
            return false;
        } catch (RuntimeException ex) {
            log.error("[EvolutionApi] Erro ao enviar PDF pela Evolution: {} [J.A.R.V.I.S. Offline]", ex.getMessage(), ex);
            return false;
        }
    }

    public String normalizeToNumber(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("@s.whatsapp.net", "").replace("whatsapp:", "");
        return cleaned.replaceAll("[^0-9]", "");
    }

    private void ensureApiConfigured() {
        if (evolutionUrl == null || evolutionUrl.isBlank()
            || evolutionApiKey == null || evolutionApiKey.isBlank()) {
            throw new IllegalStateException("Evolution API não configurada (evolution.url/evolution.apikey)");
        }
    }

    /** Exige instância global quando o override está vazio (compatível com webhook antigo). */
    private void ensureDefaultInstanceIfNeeded(String evolutionInstanceOverride) {
        String use = resolveInstanceName(evolutionInstanceOverride);
        if (use == null || use.isBlank()) {
            throw new IllegalStateException("Evolution API não configurada (evolution.instance ou instância do utilizador)");
        }
    }

    private String resolveInstanceName(String evolutionInstanceOverride) {
        if (evolutionInstanceOverride != null && !evolutionInstanceOverride.isBlank()) {
            return evolutionInstanceOverride.trim();
        }
        return evolutionInstance != null ? evolutionInstance.trim() : "";
    }

    private void registerOutgoingFromResponse(String to, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String keyId = firstNonBlank(
                root.path("key").path("id").asText(""),
                root.path("data").path("key").path("id").asText(""),
                root.path("message").path("key").path("id").asText("")
            );
            if (!keyId.isBlank()) {
                evolutionBotEchoFilterService.registerOutgoingMessageKey(keyId);
            }
        } catch (Exception e) {
            log.debug("[EvolutionApi] Resposta de envio sem key.id parseável: {}", e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return c != null && !c.isBlank() ? c.trim() : "";
    }
}
