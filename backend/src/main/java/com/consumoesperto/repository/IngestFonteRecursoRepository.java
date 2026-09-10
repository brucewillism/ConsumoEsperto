package com.consumoesperto.repository;

import com.consumoesperto.model.IngestFonteRecurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngestFonteRecursoRepository extends JpaRepository<IngestFonteRecurso, Long> {

    List<IngestFonteRecurso> findByUsuarioIdOrderByAppAscCanalAsc(Long usuarioId);

    Optional<IngestFonteRecurso> findByUsuarioIdAndAppIgnoreCaseAndCanalIgnoreCase(
        Long usuarioId, String app, String canal);

    void deleteByUsuarioId(Long usuarioId);
}
