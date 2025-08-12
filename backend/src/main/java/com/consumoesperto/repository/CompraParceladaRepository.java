package com.consumoesperto.repository;

import com.consumoesperto.model.CompraParcelada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompraParceladaRepository extends JpaRepository<CompraParcelada, Long> {

    List<CompraParcelada> findByCartaoCreditoId(Long cartaoCreditoId);

    List<CompraParcelada> findByCartaoCreditoUsuarioId(Long usuarioId);

    Optional<CompraParcelada> findByIdAndCartaoCreditoUsuarioId(Long id, Long usuarioId);

    @Query("SELECT cp FROM CompraParcelada cp WHERE cp.cartaoCredito.usuario.id = :usuarioId AND cp.statusCompra = :status")
    List<CompraParcelada> findByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") CompraParcelada.StatusCompra status);

    @Query("SELECT cp FROM CompraParcelada cp WHERE cp.cartaoCredito.usuario.id = :usuarioId AND cp.dataCompra BETWEEN :dataInicio AND :dataFim")
    List<CompraParcelada> findByUsuarioIdAndDataCompraBetween(@Param("usuarioId") Long usuarioId, 
                                                             @Param("dataInicio") LocalDateTime dataInicio, 
                                                             @Param("dataFim") LocalDateTime dataFim);

    @Query("SELECT SUM(cp.valorTotal) FROM CompraParcelada cp WHERE cp.cartaoCredito.usuario.id = :usuarioId AND cp.statusCompra = :status")
    Double getTotalCompraByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") CompraParcelada.StatusCompra status);

    @Query("SELECT cp FROM CompraParcelada cp WHERE cp.cartaoCredito.usuario.id = :usuarioId AND cp.parcelaAtual < cp.numeroParcelas AND cp.statusCompra = 'ATIVA'")
    List<CompraParcelada> findActiveInstallmentsByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT cp FROM CompraParcelada cp WHERE cp.cartaoCredito.usuario.id = :usuarioId AND cp.dataUltimaParcela <= :dataLimite AND cp.statusCompra = 'ATIVA'")
    List<CompraParcelada> findUpcomingInstallmentsByUsuarioId(@Param("usuarioId") Long usuarioId, @Param("dataLimite") LocalDateTime dataLimite);
}
