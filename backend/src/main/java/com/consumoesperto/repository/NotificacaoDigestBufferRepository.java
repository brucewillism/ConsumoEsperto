package com.consumoesperto.repository;

import com.consumoesperto.model.NotificacaoDigestBuffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface NotificacaoDigestBufferRepository extends JpaRepository<NotificacaoDigestBuffer, Long> {

    boolean existsByUsuarioIdAndHashEvento(Long usuarioId, String hashEvento);

    List<NotificacaoDigestBuffer> findByUsuarioIdAndDataRefOrderByCriadoEmAsc(Long usuarioId, LocalDate dataRef);

    @Query("SELECT DISTINCT b.usuarioId FROM NotificacaoDigestBuffer b WHERE b.dataRef = :dataRef")
    List<Long> findDistinctUsuarioIdsByDataRef(@Param("dataRef") LocalDate dataRef);

    void deleteByUsuarioIdAndDataRef(Long usuarioId, LocalDate dataRef);
}
