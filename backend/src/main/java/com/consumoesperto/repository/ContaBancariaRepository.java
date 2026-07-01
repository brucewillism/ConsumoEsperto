package com.consumoesperto.repository;

import com.consumoesperto.model.ContaBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ContaBancariaRepository extends JpaRepository<ContaBancaria, Long> {

    List<ContaBancaria> findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(Long usuarioId);

    List<ContaBancaria> findByUsuarioIdOrderByPadraoDescNomeAsc(Long usuarioId);

    @Query("SELECT c FROM ContaBancaria c JOIN FETCH c.usuario WHERE c.ativa = true")
    List<ContaBancaria> findByAtivaTrue();

    Optional<ContaBancaria> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndAtivaTrue(Long usuarioId);

    Optional<ContaBancaria> findFirstByUsuarioIdAndPadraoTrueAndAtivaTrue(Long usuarioId);

    Optional<ContaBancaria> findFirstByUsuarioIdAndAtivaTrueOrderByIdAsc(Long usuarioId);

    @Query("SELECT COALESCE(SUM(c.saldoAtual), 0) FROM ContaBancaria c "
        + "WHERE c.usuario.id = :usuarioId AND c.ativa = true")
    BigDecimal sumSaldoAtualByUsuarioIdAndAtivaTrue(@Param("usuarioId") Long usuarioId);

    @Query("SELECT COALESCE(SUM(c.saldoAtual), 0) FROM ContaBancaria c "
        + "WHERE c.usuario.id = :usuarioId AND c.ativa = true "
        + "AND c.tipo IN (com.consumoesperto.model.ContaBancaria$TipoConta.CORRENTE, "
        + "com.consumoesperto.model.ContaBancaria$TipoConta.POUPANCA, "
        + "com.consumoesperto.model.ContaBancaria$TipoConta.DINHEIRO)")
    BigDecimal sumSaldoLiquidezImediataByUsuarioId(@Param("usuarioId") Long usuarioId);
}
