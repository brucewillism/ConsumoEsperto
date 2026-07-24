package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacao_enviada", indexes = {
    @Index(name = "idx_notif_enviada_usuario_hash", columnList = "usuario_id, hash_evento"),
    @Index(name = "idx_notif_enviada_usuario_categoria_data", columnList = "usuario_id, categoria, data_envio")
})
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoEnviada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "tipo", nullable = false, length = 64)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 16)
    private NotificacaoCategoria categoria;

    @Column(name = "hash_evento", nullable = false, length = 128)
    private String hashEvento;

    @Column(name = "data_envio", nullable = false)
    private LocalDateTime dataEnvio;
}
