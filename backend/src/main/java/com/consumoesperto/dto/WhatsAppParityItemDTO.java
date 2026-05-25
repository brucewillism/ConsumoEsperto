package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Um recurso financeiro e como usá-lo no app e no WhatsApp (paridade). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppParityItemDTO {
    /** Identificador estável (ex.: transacoes-despesa). */
    private String id;
    private String titulo;
    /** Rota Angular (ex.: /transacoes); vazio se só WhatsApp. */
    private String rotaApp;
    /** Nome do menu no app. */
    private String menuApp;
    /** BOTH | APP_ONLY | WHATSAPP_ONLY */
    private String canal;
    private List<String> exemplosWhatsapp;
    private List<String> acoesApp;
    private String nota;
}
