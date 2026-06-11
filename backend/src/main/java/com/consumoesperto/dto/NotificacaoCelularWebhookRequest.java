package com.consumoesperto.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class NotificacaoCelularWebhookRequest {

    /**
     * Obrigatório no modo MacroDroid (sem JWT). No app, configure o campo com seu ID de usuário.
     */
    private Long usuarioId;

    @NotBlank(message = "texto da notificação é obrigatório")
    private String texto;

    /** Pacote Android do app bancário (ex.: com.nu.production). */
    private String app;

    /** ISO-8601 local, ex.: 2026-06-11T16:00:00 */
    private String dataEnvio;
}
