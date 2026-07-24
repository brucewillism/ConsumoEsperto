package com.consumoesperto.dto;

import com.consumoesperto.model.JarvisTipoNotificacaoProativa;
import com.consumoesperto.model.NotificacaoEventoTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Pedido de notificação — única entrada para {@link com.consumoesperto.service.NotificationOrchestratorService}.
 */
@Getter
@Builder
public class NotificacaoSolicitacao {

    private final Long usuarioId;
    private final NotificacaoEventoTipo evento;
    private final String mensagem;
    private final String hashEvento;
    /** Linha curta para digest informativo (ex.: «Score: 76 (+3)»). */
    private final String digestLinha;
    /** Título para notificação in-app / Web Push futuro. */
    private final String tituloWeb;

    public static NotificacaoSolicitacao legacy(
        Long usuarioId,
        String mensagem,
        JarvisTipoNotificacaoProativa tipoJarvis
    ) {
        NotificacaoEventoTipo evento = NotificacaoEventoTipo.fromJarvisTipo(tipoJarvis);
        return NotificacaoSolicitacao.builder()
            .usuarioId(usuarioId)
            .evento(evento)
            .mensagem(mensagem)
            .hashEvento(null)
            .digestLinha(null)
            .tituloWeb(evento.name())
            .build();
    }

    public static NotificacaoSolicitacao legacyComHash(
        Long usuarioId,
        String mensagem,
        JarvisTipoNotificacaoProativa tipoJarvis,
        String hashEvento
    ) {
        return NotificacaoSolicitacao.builder()
            .usuarioId(usuarioId)
            .evento(NotificacaoEventoTipo.fromJarvisTipo(tipoJarvis))
            .mensagem(mensagem)
            .hashEvento(hashEvento)
            .digestLinha(null)
            .tituloWeb(tipoJarvis != null ? tipoJarvis.name() : "NOTIFICACAO")
            .build();
    }
}
