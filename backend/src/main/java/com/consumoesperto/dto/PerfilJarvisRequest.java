package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Calibragem do protocolo J.A.R.V.I.S. (título de tratamento).
 * Aceita rótulo em português ou nome do enum (ex.: SENHOR, NENHUM).
 */
@Data
public class PerfilJarvisRequest {

    @NotNull(message = "tratamento é obrigatório")
    private String tratamento;
}
