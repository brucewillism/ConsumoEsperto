package com.consumoesperto.repository;

import com.consumoesperto.model.MerchantCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantCategoryRuleRepository extends JpaRepository<MerchantCategoryRule, Long> {

    List<MerchantCategoryRule> findByUsuarioIdOrderByLastUsedAtDescCreatedAtDesc(Long usuarioId);

    Optional<MerchantCategoryRule> findFirstByUsuarioIdAndMerchantNormalizedIgnoreCase(
        Long usuarioId, String merchantNormalized);

    Optional<MerchantCategoryRule> findFirstByUsuarioIdAndMerchantPatternIgnoreCase(
        Long usuarioId, String merchantPattern);
}
