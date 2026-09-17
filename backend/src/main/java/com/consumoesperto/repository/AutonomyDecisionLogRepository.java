package com.consumoesperto.repository;

import com.consumoesperto.model.AutonomyDecisionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AutonomyDecisionLogRepository extends JpaRepository<AutonomyDecisionLog, Long> {

    List<AutonomyDecisionLog> findTop20ByUsuarioIdOrderByCreatedAtDesc(Long usuarioId);

    long countByUsuarioIdAndCreatedAtGreaterThanEqualAndResult(Long usuarioId, LocalDateTime from, String result);
}
