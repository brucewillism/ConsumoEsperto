package com.consumoesperto.service;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Entrega de baixo nível via Evolution API — apenas chamado pelo orquestrador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppDeliveryService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioAiConfigRepository usuarioAiConfigRepository;
    private final EvolutionApiService evolutionApiService;
    private final JarvisProtocolService jarvisProtocolService;

    public boolean enviar(Long usuarioId, String mensagem) {
        if (usuarioId == null || mensagem == null || mensagem.isBlank()) {
            return false;
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) {
            log.debug("Usuário {} não encontrado; WhatsApp ignorado.", usuarioId);
            return false;
        }
        String destino = resolverNumeroDestino(usuario, usuarioId);
        if (destino.isBlank()) {
            log.debug("Usuário {} sem WhatsApp; entrega ignorada.", usuarioId);
            return false;
        }
        String instance = usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .map(c -> c.getEvolutionInstanceName())
            .filter(s -> s != null && !s.isBlank())
            .orElse(null);
        try {
            String body = jarvisProtocolService.assinaturaCondicional(usuarioId, mensagem);
            boolean ok = evolutionApiService.enviarMensagem(destino, body, instance);
            if (!ok) {
                log.warn("[Orquestrador] WhatsApp não enviado userId={}", usuarioId);
            }
            return ok;
        } catch (Exception e) {
            log.warn("[Orquestrador] Falha WhatsApp userId={}: {}", usuarioId, e.getMessage());
            return false;
        }
    }

    private String resolverNumeroDestino(Usuario usuario, Long usuarioId) {
        if (usuario.getWhatsappNumero() != null && !usuario.getWhatsappNumero().isBlank()) {
            return formatarNumero(usuario.getWhatsappNumero().trim());
        }
        return usuarioAiConfigRepository.findByUsuarioId(usuarioId)
            .map(c -> c.getWhatsappOwnerPhone())
            .filter(p -> p != null && !p.isBlank())
            .map(p -> formatarNumero(p.trim()))
            .orElse("");
    }

    private static String formatarNumero(String stored) {
        return stored == null ? "" : stored.replace("@s.whatsapp.net", "")
            .replace("whatsapp:", "").replaceAll("[^0-9]", "");
    }
}
