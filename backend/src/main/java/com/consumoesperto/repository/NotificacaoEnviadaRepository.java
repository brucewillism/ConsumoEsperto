package com.consumoesperto.repository;

import com.consumoesperto.model.NotificacaoCategoria;
import com.consumoesperto.model.NotificacaoEnviada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificacaoEnviadaRepository extends JpaRepository<NotificacaoEnviada, Long> {

    boolean existsByUsuarioIdAndHashEvento(Long usuarioId, String hashEvento);

    @Query("SELECT MAX(n.dataEnvio) FROM NotificacaoEnviada n "
        + "WHERE n.usuarioId = :usuarioId AND n.categoria = :categoria")
    Optional<LocalDateTime> findUltimoEnvioPorCategoria(
        @Param("usuarioId") Long usuarioId,
        @Param("categoria") NotificacaoCategoria categoria
    );
}
