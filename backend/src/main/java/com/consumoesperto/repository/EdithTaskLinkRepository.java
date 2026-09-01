package com.consumoesperto.repository;

import com.consumoesperto.model.EdithTaskLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EdithTaskLinkRepository extends JpaRepository<EdithTaskLink, Long> {

    Optional<EdithTaskLink> findByEdithTaskIdAndUsuarioId(String edithTaskId, Long usuarioId);

    Optional<EdithTaskLink> findByEdithTaskId(String edithTaskId);

    Optional<EdithTaskLink> findByUsuarioIdAndClientRequestId(Long usuarioId, String clientRequestId);

    Optional<EdithTaskLink> findByContextRef(String contextRef);
}
