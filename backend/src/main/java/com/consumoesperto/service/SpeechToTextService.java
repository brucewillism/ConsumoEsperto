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

    private final WhisperTranscriptionService whisperTranscriptionService;

    public String transcrever(byte[] audioBytes, String filename, String contentType, Long userId) {
        return whisperTranscriptionService.transcrever(audioBytes, filename, contentType, userId);
    }
}
