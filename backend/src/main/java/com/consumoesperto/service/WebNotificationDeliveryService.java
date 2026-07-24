package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Entrega in-app / preparação para Web Push — chamado apenas pelo orquestrador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebNotificationDeliveryService {

    private final NotificacaoPushService notificacaoPushService;

    /**
     * Regista notificação in-app. Web Push real será plugado aqui no futuro.
     */
    public boolean enviar(Long usuarioId, String titulo, String mensagem, String tipo) {
        if (usuarioId == null || mensagem == null || mensagem.isBlank()) {
            return false;
        }
        try {
            notificacaoPushService.criarNotificacaoPublica(
                usuarioId,
                titulo != null && !titulo.isBlank() ? titulo : "ConsumoEsperto",
                mensagem,
                tipo != null ? tipo : "INFO"
            );
            log.debug("[Orquestrador] Notificação web/in-app userId={} tipo={}", usuarioId, tipo);
            return true;
        } catch (Exception e) {
            log.warn("[Orquestrador] Falha web/in-app userId={}: {}", usuarioId, e.getMessage());
            return false;
        }
    }
}
