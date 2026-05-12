package com.consumoesperto.repository;

import com.consumoesperto.model.SugestaoContencaoJarvis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SugestaoContencaoJarvisRepository extends JpaRepository<SugestaoContencaoJarvis, Long> {

    List<SugestaoContencaoJarvis> findByUsuarioIdAndStatusOrderByValorGastoReferenciaDesc(
        Long usuarioId,
        SugestaoContencaoJarvis.Status status
    );

    List<SugestaoContencaoJarvis> findByUsuarioIdAndImportacaoFaturaCartaoIdAndStatusOrderByValorGastoReferenciaDesc(
        Long usuarioId,
        Long importacaoId,
        SugestaoContencaoJarvis.Status status
    );

    Optional<SugestaoContencaoJarvis> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndStatus(Long usuarioId, SugestaoContencaoJarvis.Status status);
}
