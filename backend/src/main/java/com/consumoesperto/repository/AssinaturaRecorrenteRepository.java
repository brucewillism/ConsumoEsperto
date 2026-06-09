package com.consumoesperto.repository;

import com.consumoesperto.model.AssinaturaRecorrente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssinaturaRecorrenteRepository extends JpaRepository<AssinaturaRecorrente, Long> {

    List<AssinaturaRecorrente> findByUsuarioIdOrderByNomeAscIdAsc(Long usuarioId);

    List<AssinaturaRecorrente> findByAtivoTrue();

    Optional<AssinaturaRecorrente> findByIdAndUsuarioId(Long id, Long usuarioId);
}
