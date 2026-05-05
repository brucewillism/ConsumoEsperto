package com.consumoesperto.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "whatsapp_lembrete_pendencia",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_lembrete_usuario_transacao_tipo",
        columnNames = {"usuario_id", "transacao_id", "tipo"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppLembretePendencia {

    public static final String TIPO_PENDENCIA_CONFERENCIA = "PENDENCIA_CONFERENCIA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "transacao_id", nullable = false)
    private Long transacaoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;
}
