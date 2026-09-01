package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.model.MobileSourceMapping;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MobileSourceMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MobileAccountResolverService {

  private final MobileSourceMappingRepository mappingRepository;
  private final CartaoCreditoRepository cartaoCreditoRepository;
  private final ContaBancariaRepository contaBancariaRepository;

  public record ResolvedAccount(Long contaId, Long cartaoId) {}

  public Optional<ResolvedAccount> resolve(
      Long usuarioId,
      Long deviceId,
      String packageName,
      String cardHint
  ) {
    List<MobileSourceMapping> mappings = packageName == null
        ? mappingRepository.findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(usuarioId)
        : mappingRepository.findByUsuarioIdAndPackageNameAndEnabledTrue(usuarioId, packageName);

  String last4 = extractLast4(cardHint);
    for (MobileSourceMapping mapping : mappings) {
      if (mapping.getDevice() != null && deviceId != null
          && mapping.getDevice().getId() != null
          && !mapping.getDevice().getId().equals(deviceId)) {
        continue;
      }
      if (last4 != null && mapping.getCardLast4() != null
          && !last4.equals(mapping.getCardLast4())) {
        continue;
      }
      Long contaId = mapping.getContaId();
      Long cartaoId = mapping.getCartaoId();
      if (contaId != null && !ownsConta(usuarioId, contaId)) {
        continue;
      }
      if (cartaoId != null && !ownsCartao(usuarioId, cartaoId)) {
        continue;
      }
      if (contaId != null || cartaoId != null) {
        return Optional.of(new ResolvedAccount(contaId, cartaoId));
      }
    }
    return Optional.empty();
  }

  private boolean ownsConta(Long usuarioId, Long contaId) {
    return contaBancariaRepository.findByIdAndUsuarioId(contaId, usuarioId).isPresent();
  }

  private boolean ownsCartao(Long usuarioId, Long cartaoId) {
    return cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId).isPresent();
  }

  private static String extractLast4(String cardHint) {
    if (cardHint == null) {
      return null;
    }
    String digits = cardHint.replaceAll("\\D", "");
    if (digits.length() >= 4) {
      return digits.substring(digits.length() - 4);
    }
    return null;
  }
}
