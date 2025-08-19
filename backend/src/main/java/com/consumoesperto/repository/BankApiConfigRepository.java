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
    Optional<BankApiConfig> findByBanco(String banco);
    List<BankApiConfig> findByAtivoTrue();
    boolean existsByBanco(String banco);
    
    // Novos métodos para configurações por usuário
    Optional<BankApiConfig> findByUsuarioIdAndBanco(Long usuarioId, String banco);
    List<BankApiConfig> findByUsuarioId(Long usuarioId);
    List<BankApiConfig> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    boolean existsByUsuarioIdAndBanco(Long usuarioId, String banco);
}
