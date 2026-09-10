package com.consumoesperto.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class WhatsappConexaoTransicaoDTO {

    String estadoAnterior;
    String estadoNovo;
    String detalhe;
    String origem;
    LocalDateTime verificadoEm;
}
