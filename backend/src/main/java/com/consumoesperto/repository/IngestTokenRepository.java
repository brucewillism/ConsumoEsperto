package com.consumoesperto.repository;

import com.consumoesperto.model.IngestToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IngestTokenRepository extends JpaRepository<IngestToken, Long> {

    @Query("SELECT t FROM IngestToken t JOIN FETCH t.usuario WHERE t.tokenHash = :hash AND t.revogado = false")
    Optional<IngestToken> findByTokenHashAndRevogadoFalse(@Param("hash") String hash);

    Optional<IngestToken> findFirstByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    Optional<IngestToken> findFirstByUsuarioIdAndRevogadoFalseOrderByCriadoEmDesc(Long usuarioId);
}
