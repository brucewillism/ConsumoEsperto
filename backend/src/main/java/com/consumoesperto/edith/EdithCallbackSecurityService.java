package com.consumoesperto.edith;

import com.consumoesperto.config.EdithProperties;
import com.consumoesperto.edith.security.EdithHmacSigner;
import com.consumoesperto.model.EdithCallbackNonce;
import com.consumoesperto.repository.EdithCallbackNonceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Validação HMAC e replay protection — contrato E.D.I.T.H. SDK 0.4.1.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EdithCallbackSecurityService {

    private final EdithProperties properties;
    private final EdithCallbackNonceRepository nonceRepository;

    @Transactional
    public void validate(String timestamp, String nonce, String requestId, byte[] body, String signature) {
        if (!properties.isEnabled()) {
            throw new EdithException(EdithErrorCode.EDITH_DISABLED, "E.D.I.T.H. desabilitada");
        }
        String secret = properties.getCallbackSecret();
        if (secret == null || secret.isBlank()) {
            throw new EdithException(EdithErrorCode.EDITH_UNAVAILABLE, "Callback secret não configurado");
        }
        if (timestamp == null || nonce == null || signature == null) {
            throw new EdithException(EdithErrorCode.CALLBACK_SIGNATURE_INVALID, "Headers de assinatura ausentes");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new EdithException(EdithErrorCode.CALLBACK_SIGNATURE_INVALID, "Timestamp inválido");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > properties.getCallbackTimestampSkewSeconds()) {
            throw new EdithException(EdithErrorCode.CALLBACK_SIGNATURE_INVALID, "Timestamp expirado");
        }

        if (nonceRepository.existsByNonce(nonce)) {
            throw new EdithException(EdithErrorCode.CALLBACK_REPLAY_DETECTED, "Nonce repetido");
        }

        String expected = EdithHmacSigner.sign(secret, timestamp, nonce, requestId, body);
        if (!EdithHmacSigner.signaturesMatch(expected, signature)) {
            throw new EdithException(EdithErrorCode.CALLBACK_SIGNATURE_INVALID, "Assinatura inválida");
        }

        LocalDateTime expires = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC)
            .plusSeconds(properties.getCallbackTimestampSkewSeconds());
        try {
            nonceRepository.save(new EdithCallbackNonce(nonce, requestId, expires));
        } catch (DataIntegrityViolationException e) {
            throw new EdithException(EdithErrorCode.CALLBACK_REPLAY_DETECTED, "Nonce repetido");
        }
    }

    @Scheduled(fixedDelayString = "${consumoesperto.edith.callback-nonce-cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredNonces() {
        nonceRepository.deleteExpiredBefore(LocalDateTime.now().minusMinutes(1));
    }
}
