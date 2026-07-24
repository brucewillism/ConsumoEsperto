package com.consumoesperto.service;

import com.consumoesperto.dto.NotificacaoSolicitacao;
import com.consumoesperto.model.JarvisTipoNotificacaoProativa;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fachada legada — delega ao {@link NotificationOrchestratorService}.
 * Não chama Evolution directamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {

    private final NotificationOrchestratorService notificationOrchestratorService;

    public boolean enviarParaUsuario(Long usuarioId, String mensagem) {
        return enviarParaUsuario(usuarioId, mensagem, null);
    }

    public boolean enviarParaUsuario(Long usuarioId, String mensagem, JarvisTipoNotificacaoProativa tipo) {
        return notificationOrchestratorService.solicitar(
            NotificacaoSolicitacao.legacy(usuarioId, mensagem, tipo));
    }
}
