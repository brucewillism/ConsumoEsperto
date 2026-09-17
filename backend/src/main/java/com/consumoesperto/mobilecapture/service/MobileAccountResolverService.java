package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.MobileSourceMapping;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MobileSourceMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MobileAccountResolverService {

  private final MobileSourceMappingRepository mappingRepository;
  private final CartaoCreditoRepository cartaoCreditoRepository;
  private final ContaBancariaRepository contaBancariaRepository;
  private final MerchantNormalizationService normalizationService;

  public record ResolvedAccount(Long contaId, Long cartaoId) {}

  public record ResolveOutcome(Optional<ResolvedAccount> account, boolean needsReview, String reason) {
    static ResolveOutcome ok(ResolvedAccount account) {
      return new ResolveOutcome(Optional.of(account), false, null);
    }
    static ResolveOutcome empty() {
      return new ResolveOutcome(Optional.empty(), false, null);
    }
    static ResolveOutcome review(String reason) {
      return new ResolveOutcome(Optional.empty(), true, reason);
    }
  }

  public Optional<ResolvedAccount> resolve(
      Long usuarioId,
      Long deviceId,
      String packageName,
      String cardHint
  ) {
    return resolveDetailed(usuarioId, deviceId, packageName, cardHint).account();
  }

  public ResolveOutcome resolveDetailed(
      Long usuarioId,
      Long deviceId,
      String packageName,
      String cardHint
  ) {
    Optional<ResolvedAccount> mapped = fromMappings(usuarioId, deviceId, packageName, cardHint);
    if (mapped.isPresent()) {
      return ResolveOutcome.ok(mapped.get());
    }

    String last4 = extractLast4(cardHint);
    if (last4 != null) {
      List<CartaoCredito> last4Hits = uniqueLast4(usuarioId, last4);
      if (last4Hits.size() == 1) {
        return ResolveOutcome.ok(new ResolvedAccount(null, last4Hits.get(0).getId()));
      }
      if (last4Hits.size() > 1) {
        return ResolveOutcome.review("card_hint last4 ambíguo");
      }
    }

    Optional<ResolvedAccount> byName = uniqueName(usuarioId, cardHint);
    if (byName.isPresent()) {
      return ResolveOutcome.ok(byName.get());
    }

    if (cardHint != null && !cardHint.isBlank()) {
      return ResolveOutcome.review("card_hint sem mapping único");
    }
    return ResolveOutcome.empty();
  }

  private Optional<ResolvedAccount> fromMappings(
      Long usuarioId,
      Long deviceId,
      String packageName,
      String cardHint
  ) {
    List<MobileSourceMapping> mappings = packageName == null
        ? mappingRepository.findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(usuarioId)
        : mappingRepository.findByUsuarioIdAndPackageNameAndEnabledTrue(usuarioId, packageName);
    if (packageName != null && mappings.isEmpty()) {
      mappings = mappingRepository.findByUsuarioIdAndEnabledTrueOrderByUpdatedAtDesc(usuarioId);
    }

    String last4 = extractLast4(cardHint);
    String hintNorm = normalizationService.normalize(cardHint);
    List<ResolvedAccount> hits = new ArrayList<>();
    for (MobileSourceMapping mapping : mappings) {
      if (mapping.getDevice() != null && deviceId != null
          && mapping.getDevice().getId() != null
          && !mapping.getDevice().getId().equals(deviceId)) {
        continue;
      }
      if (cardHint != null && !cardHint.isBlank()) {
        boolean last4Match = last4 != null && last4.equals(mapping.getCardLast4());
        String keyNorm = mapping.getProviderKey() != null
            ? normalizationService.normalize(mapping.getProviderKey()) : null;
        boolean keyMatch = hintNorm != null && keyNorm != null
            && (hintNorm.equals(keyNorm) || hintNorm.contains(keyNorm) || keyNorm.contains(hintNorm));
        if (!last4Match && !keyMatch) {
          continue;
        }
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
        hits.add(new ResolvedAccount(contaId, cartaoId));
      }
    }
    if (hits.size() == 1) {
      return Optional.of(hits.get(0));
    }
    return Optional.empty();
  }

  private List<CartaoCredito> uniqueLast4(Long usuarioId, String last4) {
    List<CartaoCredito> hits = new ArrayList<>();
    for (CartaoCredito c : cartaoCreditoRepository.findByUsuarioId(usuarioId)) {
      String digits = c.getNumeroCartao() == null ? "" : c.getNumeroCartao().replaceAll("\\D", "");
      if (digits.length() >= 4 && last4.equals(digits.substring(digits.length() - 4))) {
        hits.add(c);
      }
    }
    return hits;
  }

  private Optional<ResolvedAccount> uniqueName(Long usuarioId, String cardHint) {
    String hintNorm = normalizationService.normalize(cardHint);
    if (hintNorm == null || hintNorm.isBlank()) {
      return Optional.empty();
    }
    List<CartaoCredito> hits = new ArrayList<>();
    for (CartaoCredito c : cartaoCreditoRepository.findByUsuarioId(usuarioId)) {
      String nome = normalizationService.normalize(c.getNome());
      if (nome == null || nome.isBlank()) {
        continue;
      }
      if (nome.equals(hintNorm) || hintNorm.contains(nome) || nome.contains(hintNorm)) {
        hits.add(c);
      }
    }
    if (hits.size() == 1) {
      return Optional.of(new ResolvedAccount(null, hits.get(0).getId()));
    }
    return Optional.empty();
  }

  private boolean ownsConta(Long usuarioId, Long contaId) {
    return contaBancariaRepository.findByIdAndUsuarioId(contaId, usuarioId).isPresent();
  }

  private boolean ownsCartao(Long usuarioId, Long cartaoId) {
    return cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId).isPresent();
  }

  static String extractLast4(String cardHint) {
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
