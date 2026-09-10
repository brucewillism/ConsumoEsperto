package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** Snapshot actual da sessão WhatsApp (Evolution) por utilizador. */
@Entity
@Table(name = "whatsapp_conexao_status")
@Getter
@Setter
@NoArgsConstructor
public class WhatsappConexaoStatus {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "instance_name", length = 200)
    private String instanceName;

    @Column(name = "estado", nullable = false, length = 40)
    private String estado;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    @Column(name = "estado_desde", nullable = false)
    private LocalDateTime estadoDesde;

    @Column(name = "verificado_em", nullable = false)
    private LocalDateTime verificadoEm;

    @Column(name = "tentativas_reconexao", nullable = false)
    private int tentativasReconexao;

    @Column(name = "proxima_tentativa_em")
    private LocalDateTime proximaTentativaEm;

    @Column(name = "ultima_tentativa_em")
    private LocalDateTime ultimaTentativaEm;

    @Column(name = "ultimo_resultado_reconexao", length = 40)
    private String ultimoResultadoReconexao;

    @Column(name = "alerta_desconexao_enviado", nullable = false)
    private boolean alertaDesconexaoEnviado;
}
