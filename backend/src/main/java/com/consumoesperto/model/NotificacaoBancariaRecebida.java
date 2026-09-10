package com.consumoesperto.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacao_bancaria_recebida")
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoBancariaRecebida {

    public static final String STATUS_RECEBIDA = "RECEBIDA";
    public static final String STATUS_PROCESSANDO = "PROCESSANDO";
    public static final String STATUS_LANCADA = "LANCADA";
    public static final String STATUS_IGNORADA = "IGNORADA";
    public static final String STATUS_NAO_RECONHECIDA = "NAO_RECONHECIDA";
    public static final String STATUS_DUPLICADA = "DUPLICADA";
    public static final String STATUS_ESTORNADA = "ESTORNADA";
    public static final String STATUS_ERRO = "ERRO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "origem", nullable = false, length = 32)
    private String origem;

    @Column(name = "app", nullable = false, length = 32)
    private String app;

    @Column(name = "titulo", length = 300)
    private String titulo;

    @Column(name = "texto", nullable = false, length = 1000)
    private String texto;

    @Column(name = "recebido_em", nullable = false)
    private LocalDateTime recebidoEm;

    @Column(name = "id_externo", length = 128)
    private String idExterno;

    @Column(name = "hash_dedup", nullable = false, length = 64)
    private String hashDedup;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "transacao_id")
    private Long transacaoId;

    @Column(name = "transacao_enriquecida_id")
    private Long transacaoEnriquecidaId;

    @Column(name = "erro", length = 500)
    private String erro;

    @Column(name = "aviso_pendente", nullable = false)
    private boolean avisoPendente;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;
}
