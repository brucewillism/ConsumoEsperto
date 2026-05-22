package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ExtratorComprovanteWebhookRequest {

    /** Obrigatório no modo webhook público (MacroDroid/Tasker). */
    private Long usuarioId;

    /** Token compartilhado — validado contra consumoesperto.extrator.webhook-token */
    private String token;

    @NotBlank
    private String banco;

    @NotBlank
    private String texto;
}
