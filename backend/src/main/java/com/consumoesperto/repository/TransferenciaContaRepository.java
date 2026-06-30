package com.consumoesperto.repository;

import com.consumoesperto.model.TransferenciaConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransferenciaContaRepository extends JpaRepository<TransferenciaConta, Long> {

    List<TransferenciaConta> findByUsuarioIdOrderByDataTransferenciaDesc(Long usuarioId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransferenciaConta t WHERE t.contaDestino.id = :contaId")
    BigDecimal sumValorEntradaPorConta(@Param("contaId") Long contaId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransferenciaConta t WHERE t.contaOrigem.id = :contaId")
    BigDecimal sumValorSaidaPorConta(@Param("contaId") Long contaId);
}
