package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class WhatsappConexaoStatusDTO {

    String estado;
    String detalhe;
    String instanceName;
    LocalDateTime estadoDesde;
    LocalDateTime verificadoEm;
    Long minutosNoEstado;
    int tentativasReconexao;
    LocalDateTime proximaTentativaEm;
    LocalDateTime ultimaTentativaEm;
    String ultimoResultadoReconexao;
    boolean autoReconexaoEsgotada;
    boolean alertaDesconexaoEnviado;
    List<WhatsappConexaoTransicaoDTO> transicoes;
}
