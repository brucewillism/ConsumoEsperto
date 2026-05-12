package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class JarvisFeedbackRequest {
    @NotBlank
    private String insightId;
    @NotNull
    private Boolean positivo;
    /** NOTIFICACAO | CONTENCAO | INSIGHT_FEED */
    @NotBlank
    private String tipoAlvo;
    private String categoriaChave;
}
