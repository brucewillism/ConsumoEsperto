package com.consumoesperto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionIncomingMessageDTO {
    private String fromJid;
    private boolean fromMe;
    private String text;
    private byte[] mediaBytes;
    private String mediaMimeType;
    private String mediaType;
    private String mediaUrl;
    /** ID da mensagem (key.id) — necessário para pedir base64 desencriptado à Evolution API. */
    private String messageKeyId;
    /**
     * ACK já enviado no thread do webhook antes do {@code @Async}.
     * {@code volatile}: o processador assíncrono precisa ver o flag sem cache (evita segundo “Compreendido…”).
     */
    private volatile boolean jarvisInstantAckSent;
}
