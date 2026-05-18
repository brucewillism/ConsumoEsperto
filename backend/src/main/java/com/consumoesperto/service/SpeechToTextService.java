package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Protocolo Áudio — transcrição (Whisper / compatível) para comando de voz vindo da Evolution API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeechToTextService {

    private final OpenAiService openAiService;

    public String transcrever(byte[] audioBytes, String filename, String contentType, Long userId) {
        log.info("[JARVIS-LOG] STT início bytes={} mime={} userId={}",
            audioBytes == null ? -1 : audioBytes.length, contentType, userId);
        String out = openAiService.transcribeAudio(audioBytes, filename, contentType, userId);
        log.info("[JARVIS-LOG] STT concluído userId={} chars={}", userId, out == null ? 0 : out.length());
        return out != null ? out : "";
    }
}
