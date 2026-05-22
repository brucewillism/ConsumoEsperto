package com.consumoesperto.repository;

import com.consumoesperto.model.Renda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RendaRepository extends JpaRepository<Renda, Long> {

    List<Renda> findByUsuarioIdAndAtivaTrueOrderByDescricaoAsc(Long usuarioId);

    List<Renda> findByUsuarioIdOrderByDescricaoAsc(Long usuarioId);

    Optional<Renda> findByIdAndUsuarioId(Long id, Long usuarioId);
}
