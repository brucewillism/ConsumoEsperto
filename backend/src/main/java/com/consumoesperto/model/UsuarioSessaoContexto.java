package com.consumoesperto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_sessoes_contexto")
@Getter
@Setter
public class UsuarioSessaoContexto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 32)
    private String canal = "WHATSAPP";

    @Column(name = "chave_sessao", nullable = false, length = 128)
    private String chaveSessao;

    @Column(name = "contexto_json", nullable = false, columnDefinition = "TEXT")
    private String contextoJson;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    void touch() {
        atualizadoEm = LocalDateTime.now();
    }
}
