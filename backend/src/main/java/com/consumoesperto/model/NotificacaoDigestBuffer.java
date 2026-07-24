package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacao_digest_buffer", indexes = {
    @Index(name = "idx_notif_digest_usuario_data", columnList = "usuario_id, data_ref")
})
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoDigestBuffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "data_ref", nullable = false)
    private LocalDate dataRef;

    @Column(name = "tipo", nullable = false, length = 64)
    private String tipo;

    @Column(name = "hash_evento", nullable = false, length = 128)
    private String hashEvento;

    /** Linha curta para bullet do digest (ex.: «Score: 76 (+3)»). */
    @Column(name = "linha_digest", length = 500)
    private String linhaDigest;

    /** Mensagem completa quando não há linha digest (ex.: resumo semanal longo). */
    @Column(name = "mensagem_completa", columnDefinition = "TEXT")
    private String mensagemCompleta;

    @Column(name = "titulo_web", length = 200)
    private String tituloWeb;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
