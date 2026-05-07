package com.consumoesperto.repository;

import com.consumoesperto.model.ContrachequeImportado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContrachequeImportadoRepository extends JpaRepository<ContrachequeImportado, Long> {
    List<ContrachequeImportado> findByUsuarioIdOrderByAnoDescMesDescDataCriacaoDesc(Long usuarioId);
    List<ContrachequeImportado> findByUsuarioIdAndStatusOrderByDataCriacaoDesc(Long usuarioId, ContrachequeImportado.Status status);
    Optional<ContrachequeImportado> findByIdAndUsuarioId(Long id, Long usuarioId);
}
