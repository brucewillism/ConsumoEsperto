package com.consumoesperto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class EvolutionApiService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${evolution.url:}")
    private String evolutionUrl;

    @Value("${evolution.apikey:}")
    private String evolutionApiKey;

    @Value("${evolution.instance:}")
    private String evolutionInstance;

    public void enviarMensagem(String to, String message) {
        enviarMensagem(to, message, null);
    }

    /**
     * @param evolutionInstanceOverride nome da instância na Evolution (multitenant); se vazio usa {@link #evolutionInstance}
     */
    public void enviarMensagem(String to, String message, String evolutionInstanceOverride) {
        ensureApiConfigured();
        ensureDefaultInstanceIfNeeded(evolutionInstanceOverride);
        String number = normalizeToNumber(to);
        if (number.isBlank()) {
            throw new RuntimeException("Numero destino invalido para Evolution API");
        }

        String instance = resolveInstanceName(evolutionInstanceOverride);

        String base = evolutionUrl.endsWith("/") ? evolutionUrl.substring(0, evolutionUrl.length() - 1) : evolutionUrl;
        String url = base + "/message/sendText/" + instance;

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
            throw new RuntimeException("Falha no envio pela Evolution API: " + response.getStatusCode());
        }
    }

    /**
     * Envia PDF como documento (base64) — mesmo contrato da Evolution {@code /message/sendMedia/:instance}.
     */
    public void enviarDocumentoPdf(String to, byte[] pdfBytes, String fileName, String evolutionInstanceOverride) {
        ensureApiConfigured();
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new RuntimeException("PDF vazio");
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "documento.pdf";
        }
        String number = normalizeToNumber(to);
        if (number.isBlank()) {
            throw new RuntimeException("Numero destino invalido para Evolution API");
        }
        String instance = resolveInstanceName(evolutionInstanceOverride);
        if (instance == null || instance.isBlank()) {
            throw new RuntimeException(
                "Instancia Evolution ausente: defina evolution.instance no servidor ou o nome da instancia nas configuracoes IA/WhatsApp do utilizador."
            );
        }
        String base = evolutionUrl.endsWith("/") ? evolutionUrl.substring(0, evolutionUrl.length() - 1) : evolutionUrl;
        String url = base + "/message/sendMedia/" + instance;

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
            throw new RuntimeException("Falha ao enviar documento pela Evolution API: " + response.getStatusCode());
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
            throw new RuntimeException("Evolution API nao configurada (evolution.url/evolution.apikey)");
        }
    }

    /** Exige instância global quando o override está vazio (compatível com webhook antigo). */
    private void ensureDefaultInstanceIfNeeded(String evolutionInstanceOverride) {
        String use = resolveInstanceName(evolutionInstanceOverride);
        if (use == null || use.isBlank()) {
            throw new RuntimeException("Evolution API nao configurada (evolution.instance ou instancia do utilizador)");
        }
    }

    private String resolveInstanceName(String evolutionInstanceOverride) {
        if (evolutionInstanceOverride != null && !evolutionInstanceOverride.isBlank()) {
            return evolutionInstanceOverride.trim();
        }
        return evolutionInstance != null ? evolutionInstance.trim() : "";
    }
}
