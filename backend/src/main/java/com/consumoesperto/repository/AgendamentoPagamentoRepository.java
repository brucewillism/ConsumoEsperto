package com.consumoesperto.repository;

import com.consumoesperto.model.AgendamentoPagamento;
import com.consumoesperto.model.AgendamentoPagamento.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgendamentoPagamentoRepository extends JpaRepository<AgendamentoPagamento, Long> {

    @Query("SELECT a FROM AgendamentoPagamento a JOIN FETCH a.contaDebito "
        + "WHERE a.usuario.id = :usuarioId ORDER BY a.dataVencimento ASC, a.id ASC")
    List<AgendamentoPagamento> findByUsuarioIdOrderByVencimento(@Param("usuarioId") Long usuarioId);

    Optional<AgendamentoPagamento> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("SELECT a FROM AgendamentoPagamento a JOIN FETCH a.contaDebito JOIN FETCH a.usuario "
        + "WHERE a.status = :status AND a.dataVencimento = :data")
    List<AgendamentoPagamento> findByStatusAndDataVencimento(
        @Param("status") StatusAgendamento status,
        @Param("data") LocalDate data
    );

    @Query("SELECT COALESCE(SUM(a.valor), 0) FROM AgendamentoPagamento a "
        + "WHERE a.usuario.id = :usuarioId AND a.status = 'AGENDADO' AND a.dataVencimento >= :hoje")
    java.math.BigDecimal sumAgendadosFuturos(@Param("usuarioId") Long usuarioId, @Param("hoje") LocalDate hoje);
}
