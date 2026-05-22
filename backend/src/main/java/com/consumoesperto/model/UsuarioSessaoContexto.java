package com.consumoesperto.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
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
