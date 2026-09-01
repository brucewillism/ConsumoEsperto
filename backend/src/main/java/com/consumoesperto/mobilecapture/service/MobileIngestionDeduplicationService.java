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
import java.util.HexFormat;
import java.util.Optional;

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
    String window = occurredAt == null
        ? "na"
        : occurredAt.withMinute(0).withSecond(0).withNano(0).toString();
    String payload = usuarioId + "|" + origem + "|" + contaId + "|" + cartaoId + "|"
        + amount.stripTrailingZeros().toPlainString() + "|" + merchantNormalized + "|" + window;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }

  public String fingerprintPrefix(String fingerprint) {
    return DeviceTokenHasher.fingerprintPrefix(fingerprint);
  }
}
