package com.consumoesperto.repository;

import com.consumoesperto.model.UsuarioAiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioAiConfigRepository extends JpaRepository<UsuarioAiConfig, Long> {

    Optional<UsuarioAiConfig> findByUsuarioId(Long usuarioId);

    Optional<UsuarioAiConfig> findByEvolutionInstanceNameIgnoreCase(String evolutionInstanceName);
}
