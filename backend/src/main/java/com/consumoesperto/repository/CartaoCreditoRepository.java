package com.consumoesperto.repository;

import com.consumoesperto.model.CartaoCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartaoCreditoRepository extends JpaRepository<CartaoCredito, Long> {

    List<CartaoCredito> findByUsuarioId(Long usuarioId);

    List<CartaoCredito> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    Optional<CartaoCredito> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByNumeroCartaoAndUsuarioId(String numeroCartao, Long usuarioId);

    Optional<CartaoCredito> findByUsuarioAndBancoAndNumeroCartao(Long usuarioId, String banco, String numeroCartao);

    @Query("SELECT c FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.ativo = true")
    List<CartaoCredito> findActiveCardsByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT SUM(c.limiteCredito) FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.ativo = true")
    Double getTotalCreditLimitByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT SUM(c.limiteDisponivel) FROM CartaoCredito c WHERE c.usuario.id = :usuarioId AND c.ativo = true")
    Double getTotalAvailableLimitByUsuarioId(@Param("usuarioId") Long usuarioId);
    
    /**
     * Busca cartão por usuário e nome
     * 
     * @param usuarioId ID do usuário
     * @param nome Nome do cartão
     * @return Cartão encontrado ou null
     */
    CartaoCredito findByUsuarioIdAndNome(Long usuarioId, String nome);
}
