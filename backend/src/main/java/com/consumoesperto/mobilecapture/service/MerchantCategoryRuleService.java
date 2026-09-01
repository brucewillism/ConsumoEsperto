package com.consumoesperto.mobilecapture.service;

import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.MerchantCategoryRule;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.MerchantCategoryRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MerchantCategoryRuleService {

  private final MerchantCategoryRuleRepository ruleRepository;
  private final CategoriaRepository categoriaRepository;
  private final MerchantNormalizationService normalizationService;

  public Optional<CategoryMatch> match(Long usuarioId, String merchantRaw) {
    String normalized = normalizationService.normalize(merchantRaw);
    if (normalized == null) {
      return Optional.empty();
    }
    Optional<MerchantCategoryRule> byNorm = ruleRepository
        .findFirstByUsuarioIdAndMerchantNormalizedIgnoreCase(usuarioId, normalized);
    if (byNorm.isPresent()) {
      return toMatch(byNorm.get());
    }
    return ruleRepository.findByUsuarioIdOrderByLastUsedAtDescCreatedAtDesc(usuarioId).stream()
        .filter(r -> normalized.contains(r.getMerchantPattern().toUpperCase())
            || r.getMerchantPattern().toUpperCase().contains(normalized))
        .findFirst()
        .flatMap(this::toMatch);
  }

  @Transactional
  public void saveUserRule(Long usuarioId, String merchantRaw, Long categoriaId) {
    Categoria categoria = categoriaRepository.findByIdAndUsuarioId(categoriaId, usuarioId)
        .orElseThrow(() -> new IllegalArgumentException("Categoria inválida"));
    String normalized = normalizationService.normalize(merchantRaw);
    MerchantCategoryRule rule = ruleRepository
        .findFirstByUsuarioIdAndMerchantPatternIgnoreCase(usuarioId, normalized)
        .orElseGet(MerchantCategoryRule::new);
    rule.setUsuario(categoria.getUsuario());
    rule.setMerchantPattern(normalized);
    rule.setMerchantNormalized(normalized);
    rule.setCategoria(categoria);
    rule.setConfidence(BigDecimal.ONE);
    rule.setOrigin("USER");
    rule.setLastUsedAt(LocalDateTime.now());
    ruleRepository.save(rule);
  }

  private Optional<CategoryMatch> toMatch(MerchantCategoryRule rule) {
    rule.setLastUsedAt(LocalDateTime.now());
    ruleRepository.save(rule);
    return Optional.of(new CategoryMatch(
        rule.getCategoria().getId(),
        rule.getConfidence() != null ? rule.getConfidence() : BigDecimal.ONE,
        "RULE"));
  }

  public record CategoryMatch(Long categoriaId, BigDecimal confidence, String origin) {}
}
