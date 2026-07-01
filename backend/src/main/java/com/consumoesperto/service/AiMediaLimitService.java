package com.consumoesperto.service;

import com.consumoesperto.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limites centralizados de tamanho de PDF/áudio antes de enviar ao LLM ou OCR.
 */
@Service
@Slf4j
public class AiMediaLimitService {

    @Value("${consumoesperto.ai.media.max-pdf-bytes:15728640}")
    private long maxPdfBytes;

    @Value("${consumoesperto.ai.media.max-audio-bytes:26214400}")
    private long maxAudioBytes;

    public void checkPdf(byte[] bytes) {
        check(bytes, maxPdfBytes, "PDF");
    }

    public void checkAudio(byte[] bytes) {
        check(bytes, maxAudioBytes, "áudio");
    }

    private void check(byte[] bytes, long maxBytes, String tipo) {
        if (bytes == null) {
            return;
        }
        if (bytes.length > maxBytes) {
            log.warn("[AI-MEDIA-LIMIT] {} excedeu limite: {} bytes (max {})", tipo, bytes.length, maxBytes);
            throw new AiUnavailableException(
                "O " + tipo + " é grande demais para processar pelo assistente. "
                    + "Envie um ficheiro menor ou use o app web.");
        }
    }
}
