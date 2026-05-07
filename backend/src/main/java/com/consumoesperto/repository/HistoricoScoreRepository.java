package com.consumoesperto.repository;

import com.consumoesperto.model.HistoricoScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoricoScoreRepository extends JpaRepository<HistoricoScore, Long> {
    List<HistoricoScore> findTop10ByUsuarioIdOrderByDataEventoDesc(Long usuarioId);
    List<HistoricoScore> findByUsuarioIdAndDataEventoBetweenOrderByDataEventoAsc(Long usuarioId, LocalDateTime inicio, LocalDateTime fim);
}
