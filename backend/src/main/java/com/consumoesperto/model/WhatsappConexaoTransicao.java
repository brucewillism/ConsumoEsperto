package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** Histórico append-only de transições de estado da sessão WhatsApp. */
@Entity
@Table(
    name = "whatsapp_conexao_transicao",
    indexes = {
        @Index(name = "idx_wa_conexao_transicao_user", columnList = "usuario_id, verificado_em")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class WhatsappConexaoTransicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "instance_name", length = 200)
    private String instanceName;

    @Column(name = "estado_anterior", length = 40)
    private String estadoAnterior;

    @Column(name = "estado_novo", nullable = false, length = 40)
    private String estadoNovo;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    @Column(name = "origem", nullable = false, length = 40)
    private String origem;

    @Column(name = "verificado_em", nullable = false)
    private LocalDateTime verificadoEm;
}
