package com.consumoesperto.repository;

import com.consumoesperto.model.RendaConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RendaConfigRepository extends JpaRepository<RendaConfig, Long> {

    Optional<RendaConfig> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = "usuario")
    List<RendaConfig> findByReceitaAutomaticaAtivaIsTrue();
}
