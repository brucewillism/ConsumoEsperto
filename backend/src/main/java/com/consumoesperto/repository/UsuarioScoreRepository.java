package com.consumoesperto.repository;

import com.consumoesperto.model.UsuarioScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioScoreRepository extends JpaRepository<UsuarioScore, Long> {
    Optional<UsuarioScore> findByUsuarioId(Long usuarioId);
}
