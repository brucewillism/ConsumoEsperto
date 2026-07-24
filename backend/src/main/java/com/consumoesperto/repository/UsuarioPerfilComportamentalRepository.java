package com.consumoesperto.repository;

import com.consumoesperto.model.UsuarioPerfilComportamental;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioPerfilComportamentalRepository extends JpaRepository<UsuarioPerfilComportamental, Long> {

    Optional<UsuarioPerfilComportamental> findTopByUsuarioIdOrderByCalculadoEmDesc(Long usuarioId);

    List<UsuarioPerfilComportamental> findTop10ByUsuarioIdOrderByCalculadoEmDesc(Long usuarioId);
}
