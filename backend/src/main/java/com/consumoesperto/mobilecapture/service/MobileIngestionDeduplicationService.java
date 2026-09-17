package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.mobilecapture.security.DeviceTokenHasher;
import com.consumoesperto.model.MobileCaptureDevice;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.repository.MobileCaptureEventRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Dedup = mesmo evento. Fingerprint exacto (não trunca hora).
 * Semelhança 10:00 vs 10:04 NÃO é dedup — isso é possível duplicata no engine.
 */
@Service
@RequiredArgsConstructor
public class MobileIngestionDeduplicationService {

  private final MobileCaptureEventRepository eventRepository;
  private final TransacaoRepository transacaoRepository;

  public Optional<Long> findDuplicateEventId(MobileCaptureDevice device, String clientEventId) {
    if (clientEventId == null || clientEventId.isBlank()) {
      return Optional.empty();
    }
    return eventRepository.findByDeviceIdAndClientEventId(device.getId(), clientEventId.trim())
        .map(e -> e.getId());
  }

  public Optional<Long> findDuplicateByFingerprint(Long usuarioId, String fingerprint) {
    if (fingerprint == null || fingerprint.isBlank()) {
      return Optional.empty();
    }
    return eventRepository.findByUsuarioIdAndFingerprint(usuarioId, fingerprint)
        .filter(e -> e.getStatus() == com.consumoesperto.model.IngestionEventStatus.REGISTERED
            || e.getStatus() == com.consumoesperto.model.IngestionEventStatus.DUPLICATE)
        .map(e -> e.getId());
  }

  public boolean existsRegisteredTransaction(Long usuarioId, String fingerprint) {
    if (fingerprint == null || fingerprint.isBlank()) {
      return false;
    }
    return transacaoRepository.existsByUsuarioIdAndIngestionFingerprint(usuarioId, fingerprint);
  }

  public String buildFingerprint(
      Long usuarioId,
      OrigemTransacao origem,
      Long contaId,
      Long cartaoId,
      BigDecimal amount,
      String merchantNormalized,
      LocalDateTime occurredAt
  ) {
    return buildFingerprint(usuarioId, origem, null, contaId, cartaoId, amount, merchantNormalized, occurredAt);
  }

  public String buildFingerprint(
      Long usuarioId,
      OrigemTransacao origem,
      Long deviceId,
      Long contaId,
      Long cartaoId,
      BigDecimal amount,
      String merchantNormalized,
      LocalDateTime occurredAt
  ) {
    String ts = occurredAt == null
        ? "na"
        : occurredAt.truncatedTo(ChronoUnit.SECONDS).toString();
    String amt = amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
    String payload = usuarioId + "|" + origem + "|" + deviceId + "|" + contaId + "|" + cartaoId + "|"
        + amt + "|" + merchantNormalized + "|" + ts;
    return sha256(payload);
  }

  /** Identificador derivado do evento exacto — não é chave fuzzy. */
  public String derivedClientEventId(String fingerprint) {
    if (fingerprint == null || fingerprint.isBlank()) {
      return null;
    }
    return "derived:" + fingerprint;
  }

  public String fingerprintPrefix(String fingerprint) {
    return DeviceTokenHasher.fingerprintPrefix(fingerprint);
  }

  private static String sha256(String payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }
}
