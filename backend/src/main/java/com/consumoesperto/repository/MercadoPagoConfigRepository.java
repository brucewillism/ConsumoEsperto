package com.consumoesperto.repository;

import com.consumoesperto.model.MercadoPagoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório para configurações do Mercado Pago
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Repository
public interface MercadoPagoConfigRepository extends JpaRepository<MercadoPagoConfig, Long> {

    /**
     * Busca configuração ativa por usuário
     */
    Optional<MercadoPagoConfig> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    /**
     * Busca todas as configurações de um usuário
     */
    List<MercadoPagoConfig> findByUsuarioId(Long usuarioId);

    /**
     * Verifica se usuário possui configuração ativa
     */
    boolean existsByUsuarioIdAndAtivoTrue(Long usuarioId);

    /**
     * Busca configurações ativas
     */
    @Query("SELECT mpc FROM MercadoPagoConfig mpc WHERE mpc.ativo = true")
    List<MercadoPagoConfig> findAllAtivas();

    /**
     * Busca configurações por sandbox
     */
    List<MercadoPagoConfig> findBySandbox(Boolean sandbox);

    /**
     * Busca configurações por usuário e sandbox
     */
    Optional<MercadoPagoConfig> findByUsuarioIdAndSandbox(Long usuarioId, Boolean sandbox);
}
