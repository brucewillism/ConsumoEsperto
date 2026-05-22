package com.consumoesperto.repository;

import com.consumoesperto.model.UsuarioSessaoContexto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UsuarioSessaoContextoRepository extends JpaRepository<UsuarioSessaoContexto, Long> {

    Optional<UsuarioSessaoContexto> findByUsuarioIdAndCanalAndChaveSessao(
        Long usuarioId,
        String canal,
        String chaveSessao
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UsuarioSessaoContexto s WHERE s.expiraEm IS NOT NULL AND s.expiraEm < :agora")
    int deleteExpiradas(@Param("agora") LocalDateTime agora);
}
