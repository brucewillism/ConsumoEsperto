package com.consumoesperto.service;

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

import java.util.Map;

/**
 * Protocolo Áudio — síntese ElevenLabs ({@code eleven_multilingual_v2} por omissão).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextToSpeechService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${elevenlabs.api-key:}")
    private String apiKey;

    @Value("${elevenlabs.voice-id:}")
    private String voiceId;

    @Value("${elevenlabs.model-id:eleven_multilingual_v2}")
    private String modelId;

    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank() && voiceId != null && !voiceId.isBlank();
    }

    public byte[] sintetizarMp3(String texto) {
        if (texto == null || texto.isBlank() || !configurado()) {
            log.info("[JARVIS-LOG] TTS ignorado (texto vazio ou ElevenLabs não configurado).");
            return null;
        }
        String limpo = texto.replace('*', ' ').replace('_', ' ').trim();
        if (limpo.length() > 4500) {
            limpo = limpo.substring(0, 4500);
        }
        try {
            String url = "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId.trim();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("xi-api-key", apiKey.trim());
            Map<String, Object> body = Map.of(
                "text", limpo,
                "model_id", modelId != null && !modelId.isBlank() ? modelId.trim() : "eleven_multilingual_v2"
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().length == 0) {
                log.warn("[JARVIS-LOG] TTS ElevenLabs HTTP {}", response.getStatusCode());
                return null;
            }
            log.info("[JARVIS-LOG] TTS ElevenLabs ok bytes={}", response.getBody().length);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[JARVIS-LOG] TTS ElevenLabs falhou: {}", e.getMessage());
            return null;
        }
    }
}
