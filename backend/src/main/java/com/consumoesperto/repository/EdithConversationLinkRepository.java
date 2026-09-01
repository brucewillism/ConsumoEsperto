package com.consumoesperto.repository;

import com.consumoesperto.model.EdithConversationLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EdithConversationLinkRepository extends JpaRepository<EdithConversationLink, Long> {

    Optional<EdithConversationLink> findByEdithConversationIdAndUsuarioId(String edithConversationId, Long usuarioId);

    Optional<EdithConversationLink> findByEdithConversationId(String edithConversationId);

    List<EdithConversationLink> findByUsuarioIdOrderByUpdatedAtDesc(Long usuarioId);
}
