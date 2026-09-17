package com.consumoesperto.repository;

import com.consumoesperto.model.AutonomyReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutonomyReviewItemRepository extends JpaRepository<AutonomyReviewItem, Long> {

    List<AutonomyReviewItem> findByUsuarioIdAndStatusOrderByCreatedAtDesc(Long usuarioId, String status);

    long countByUsuarioIdAndStatus(Long usuarioId, String status);

    long countByUsuarioIdAndStatusAndKind(Long usuarioId, String status, String kind);

    java.util.Optional<AutonomyReviewItem> findFirstByUsuarioIdAndKindAndTransacaoIdAndStatus(
        Long usuarioId, String kind, Long transacaoId, String status);

    java.util.Optional<AutonomyReviewItem> findFirstByUsuarioIdAndKindAndTitleAndStatus(
        Long usuarioId, String kind, String title, String status);
}
