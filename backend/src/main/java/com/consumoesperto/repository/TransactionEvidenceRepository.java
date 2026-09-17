package com.consumoesperto.repository;

import com.consumoesperto.model.TransactionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionEvidenceRepository extends JpaRepository<TransactionEvidence, Long> {

    List<TransactionEvidence> findByTransacaoIdOrderByFirstSeenAtAsc(Long transacaoId);

    Optional<TransactionEvidence> findFirstByUsuarioIdAndSourceAndExternalId(
        Long usuarioId, String source, String externalId);

    Optional<TransactionEvidence> findFirstByTransacaoIdAndSource(Long transacaoId, String source);

    long countByTransacaoId(Long transacaoId);

    long countByUsuarioIdAndFirstSeenAtGreaterThanEqual(Long usuarioId, java.time.LocalDateTime from);
}
