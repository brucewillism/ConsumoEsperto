package com.consumoesperto.repository;

import com.consumoesperto.model.BankApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório para configurações das APIs bancárias
 */
@Repository
public interface BankApiConfigRepository extends JpaRepository<BankApiConfig, Long> {
    Optional<BankApiConfig> findByBankCode(String bankCode);
    Optional<BankApiConfig> findByBankName(String bankName);
    List<BankApiConfig> findByIsActiveTrue();
    boolean existsByBankCode(String bankCode);
    
    // Novos métodos para configurações por usuário
    Optional<BankApiConfig> findByUsuarioIdAndBankCode(Long usuarioId, String bankCode);
    List<BankApiConfig> findByUsuarioId(Long usuarioId);
    List<BankApiConfig> findByUsuarioIdAndIsActiveTrue(Long usuarioId);
    boolean existsByUsuarioIdAndBankCode(Long usuarioId, String bankCode);
}
