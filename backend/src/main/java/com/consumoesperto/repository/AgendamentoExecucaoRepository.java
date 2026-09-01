package com.consumoesperto.repository;

import com.consumoesperto.model.AgendamentoExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AgendamentoExecucaoRepository extends JpaRepository<AgendamentoExecucao, Long> {

    boolean existsByAgendamentoIdAndDataExecucao(Long agendamentoId, LocalDate dataExecucao);
}
