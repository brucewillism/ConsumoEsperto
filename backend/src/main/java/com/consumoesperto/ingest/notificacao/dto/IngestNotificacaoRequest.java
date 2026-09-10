package com.consumoesperto.ingest.notificacao.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class IngestNotificacaoRequest {

    private String origem;
    private String app;
    private String titulo;
    private String texto;
    private OffsetDateTime recebidoEm;
    private String idExterno;
}
