package com.consumoesperto.repository;

import com.consumoesperto.model.GrupoFamiliarMembro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoFamiliarMembroRepository extends JpaRepository<GrupoFamiliarMembro, Long> {
    Optional<GrupoFamiliarMembro> findByGrupoFamiliarIdAndUsuarioId(Long grupoId, Long usuarioId);
    Optional<GrupoFamiliarMembro> findByTokenConvite(String token);

    @Query("SELECT m FROM GrupoFamiliarMembro m JOIN FETCH m.grupoFamiliar g LEFT JOIN FETCH m.usuario u "
        + "WHERE m.usuario.id = :usuarioId AND m.status = com.consumoesperto.model.GrupoFamiliarMembro$Status.ACEITO")
    List<GrupoFamiliarMembro> findAceitosByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT m FROM GrupoFamiliarMembro m LEFT JOIN FETCH m.usuario u "
        + "WHERE m.grupoFamiliar.id = :grupoId ORDER BY m.id")
    List<GrupoFamiliarMembro> findByGrupoFamiliarIdFetchUsuario(@Param("grupoId") Long grupoId);

    @Query("SELECT m FROM GrupoFamiliarMembro m JOIN FETCH m.grupoFamiliar g "
        + "WHERE m.status = com.consumoesperto.model.GrupoFamiliarMembro$Status.PENDENTE "
        + "AND (LOWER(m.conviteEmail) = LOWER(:email) OR m.conviteWhatsapp = :whatsapp)")
    List<GrupoFamiliarMembro> findPendentesParaIdentidade(@Param("email") String email, @Param("whatsapp") String whatsapp);
}
