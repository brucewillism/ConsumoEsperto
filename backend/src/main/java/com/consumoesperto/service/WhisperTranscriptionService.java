package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Jarvis v8 — transcrição de áudio WhatsApp via {@code gpt-4o-transcribe} (ou modelo configurável).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhisperTranscriptionService {

    private final OpenAiService openAiService;

    public String transcrever(byte[] audioBytes, String filename, String contentType, Long userId) {
        log.info("[JARVIS-VOICE] STT início bytes={} mime={} userId={}",
            audioBytes == null ? -1 : audioBytes.length, contentType, userId);
        String out = openAiService.transcribeAudio(audioBytes, filename, contentType, userId);
        log.info("[JARVIS-VOICE] STT concluído userId={} chars={}", userId, out == null ? 0 : out.length());
        return out != null ? out.trim() : "";
    }

    /** Retorna vazio em falha ou transcrição em branco (sem propagar exceção ao webhook). */
    public Optional<String> transcreverSeguro(byte[] audioBytes, String filename, String contentType, Long userId) {
        try {
            String texto = transcrever(audioBytes, filename, contentType, userId);
            if (texto == null || texto.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(texto);
        } catch (Exception e) {
            log.warn("[JARVIS-VOICE] Falha na transcrição userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
