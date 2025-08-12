package com.consumoesperto.repository;

import com.consumoesperto.model.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {

    /**
     * Busca parcelas por compra parcelada
     */
    List<Parcela> findByCompraParceladaIdOrderByNumeroParcela(Long compraParceladaId);

    /**
     * Busca parcelas por status
     */
    List<Parcela> findByStatus(Parcela.StatusParcela status);

    /**
     * Busca parcelas vencidas até uma data específica
     */
    List<Parcela> findByDataVencimentoBeforeAndStatus(LocalDate data, Parcela.StatusParcela status);

    /**
     * Busca parcelas por usuário (através da compra parcelada)
     */
    @Query("SELECT p FROM Parcela p JOIN p.compraParcelada cp WHERE cp.usuario.id = :usuarioId")
    List<Parcela> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Busca parcelas pendentes por usuário
     */
    @Query("SELECT p FROM Parcela p JOIN p.compraParcelada cp WHERE cp.usuario.id = :usuarioId AND p.status = 'PENDENTE'")
    List<Parcela> findPendentesByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Busca parcelas vencidas por usuário
     */
    @Query("SELECT p FROM Parcela p JOIN p.compraParcelada cp WHERE cp.usuario.id = :usuarioId AND p.status = 'VENCIDA'")
    List<Parcela> findVencidasByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Conta parcelas por status para um usuário
     */
    @Query("SELECT COUNT(p) FROM Parcela p JOIN p.compraParcelada cp WHERE cp.usuario.id = :usuarioId AND p.status = :status")
    Long countByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") Parcela.StatusParcela status);
}
