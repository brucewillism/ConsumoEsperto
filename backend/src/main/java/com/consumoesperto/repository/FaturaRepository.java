package com.consumoesperto.repository;

import com.consumoesperto.model.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    List<Fatura> findByCartaoCreditoId(Long cartaoCreditoId);

    List<Fatura> findByCartaoCreditoIdOrderByDataVencimentoAsc(Long cartaoCreditoId);

    List<Fatura> findByCartaoCreditoUsuarioId(Long usuarioId);

    Optional<Fatura> findByIdAndCartaoCreditoUsuarioId(Long id, Long usuarioId);

    Optional<Fatura> findByCartaoCreditoIdAndNumeroFatura(Long cartaoCreditoId, String numeroFatura);

    List<Fatura> findByCartaoCreditoIdAndStatusInOrderByDataVencimentoAsc(Long cartaoCreditoId, List<Fatura.StatusFatura> statuses);

    @Query("SELECT f FROM Fatura f WHERE f.cartaoCredito.usuario.id = :usuarioId AND f.status = :status")
    List<Fatura> findByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") Fatura.StatusFatura status);

    @Query("SELECT f FROM Fatura f WHERE f.cartaoCredito.usuario.id = :usuarioId AND f.dataVencimento BETWEEN :dataInicio AND :dataFim")
    List<Fatura> findByUsuarioIdAndDataVencimentoBetween(@Param("usuarioId") Long usuarioId, 
                                                        @Param("dataInicio") LocalDateTime dataInicio, 
                                                        @Param("dataFim") LocalDateTime dataFim);

    @Query("SELECT SUM(f.valorTotal) FROM Fatura f WHERE f.cartaoCredito.usuario.id = :usuarioId AND f.status = :status")
    Double getTotalFaturaByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") Fatura.StatusFatura status);

    @Query("SELECT f FROM Fatura f WHERE f.cartaoCredito.usuario.id = :usuarioId AND f.dataVencimento <= :dataLimite AND f.status = 'VENCIDA'")
    List<Fatura> findVencidasByUsuarioId(@Param("usuarioId") Long usuarioId, @Param("dataLimite") LocalDateTime dataLimite);
    
    int deleteByCartaoCreditoUsuarioId(Long usuarioId);

    long countByCartaoCreditoId(Long cartaoCreditoId);

    @Query("SELECT COALESCE(SUM(f.valorFatura), 0) FROM Fatura f WHERE f.cartaoCredito.id = :cartaoId "
        + "AND f.status IN (com.consumoesperto.model.Fatura$StatusFatura.ABERTA, com.consumoesperto.model.Fatura$StatusFatura.PARCIAL)")
    BigDecimal sumValorFaturasAbertasPorCartaoId(@Param("cartaoId") Long cartaoId);
}
