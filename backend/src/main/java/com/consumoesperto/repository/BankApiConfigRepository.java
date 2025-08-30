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
    Optional<BankApiConfig> findByTipoBanco(String tipoBanco);
    List<BankApiConfig> findByAtivoTrue();
    boolean existsByTipoBanco(String tipoBanco);
    
    // Novos métodos para configurações por usuário
    Optional<BankApiConfig> findByUsuarioIdAndTipoBanco(Long usuarioId, String tipoBanco);
    List<BankApiConfig> findByUsuarioId(Long usuarioId);
    List<BankApiConfig> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    boolean existsByUsuarioIdAndTipoBanco(Long usuarioId, String tipoBanco);
    
    // Métodos de compatibilidade para código existente
    default Optional<BankApiConfig> findByUsuarioIdAndBanco(Long usuarioId, String banco) {
        return findByUsuarioIdAndTipoBanco(usuarioId, banco);
    }
    
    default Optional<BankApiConfig> findByBanco(String banco) {
        return findByTipoBanco(banco);
    }
    
    default boolean existsByUsuarioIdAndBanco(Long usuarioId, String banco) {
        return existsByUsuarioIdAndTipoBanco(usuarioId, banco);
    }
}
