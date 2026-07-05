package com.consumoesperto.repository;

import com.consumoesperto.model.MovimentacaoSaldoLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Append-only: somente INSERT e leitura; nunca UPDATE/DELETE fora do expurgo por retenção. */
public interface MovimentacaoSaldoLogRepository extends JpaRepository<MovimentacaoSaldoLog, Long> {

    @Query("SELECT m FROM MovimentacaoSaldoLog m WHERE m.contaId = :contaId ORDER BY m.id DESC")
    List<MovimentacaoSaldoLog> findUltimasPorConta(@Param("contaId") Long contaId, Pageable pageable);

    Optional<MovimentacaoSaldoLog> findTopByContaIdOrderByIdDesc(Long contaId);
}
