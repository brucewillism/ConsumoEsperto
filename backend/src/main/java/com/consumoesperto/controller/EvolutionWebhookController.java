package com.consumoesperto.controller;

import com.consumoesperto.dto.EvolutionIncomingMessageDTO;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.service.AiProvidersConfigService;
import com.consumoesperto.service.EvolutionWebhookAsyncProcessor;
import com.consumoesperto.service.EvolutionWebhookDedupService;
import com.consumoesperto.service.JarvisProtocolService;
import com.consumoesperto.service.WhatsappAccountProvisioner;
import com.consumoesperto.service.WhatsAppBotAllowlist;
import com.consumoesperto.service.WhatsAppCommandService;
import com.consumoesperto.service.WhatsAppUserMappingService;
import com.consumoesperto.service.WhatsAppWebhookPolicyService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/public/evolution", "/api/whatsapp"})
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookController {

    private final WhatsAppBotAllowlist whatsAppBotAllowlist;
    private final AiProvidersConfigService aiProvidersConfigService;
    private final WhatsAppUserMappingService whatsAppUserMappingService;
    private final WhatsappAccountProvisioner whatsappAccountProvisioner;
    private final EvolutionWebhookDedupService evolutionWebhookDedupService;
    private final WhatsAppWebhookPolicyService whatsAppWebhookPolicyService;
    private final EvolutionWebhookAsyncProcessor evolutionWebhookAsyncProcessor;
    private final WhatsAppCommandService whatsAppCommandService;
    private final JarvisProtocolService jarvisProtocolService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> receiveEvolutionWebhook(@RequestBody JsonNode payload) {
        String event = firstNonBlank(
            payload.path("event").asText(""),
            payload.path("body").path("event").asText(""),
            payload.path("data").path("event").asText(""),
            payload.path("type").asText("")
        );
        if (!isMessagesUpsertEvent(event)) {
            log.info("Evolution webhook ignorado (nao e mensagem): event='{}'", event);
            return ResponseEntity.ok(Map.of("status", "ignored", "event", event));
        }

        JsonNode data = normalizeWebhookData(payload);
        if (data == null || data.isMissingNode() || data.isNull() || !data.isObject() || !data.has("key")) {
            log.warn("[WhatsAppFilter] Payload Evolution sem bloco key válido — ignorado.");
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "empty-payload"));
        }

        JsonNode key = data.path("key");
        String effectiveRemote = extractEffectiveRemoteJid(key, payload);
        String messageKeyId = key.path("id").asText("");
        boolean fromMeFlag = key.path("fromMe").asBoolean(false);
        String dedupBodySnippet = extractTextPreviewForDedup(data);
        String instance = firstNonBlank(
            payload.path("instance").asText(""),
            payload.path("instanceName").asText(""),
            payload.path("body").path("instance").asText(""),
            data.path("instanceName").asText("")
        );

        if (effectiveRemote.endsWith("@g.us") || effectiveRemote.contains("@g.us")
            || effectiveRemote.endsWith("@broadcast") || "status@broadcast".equalsIgnoreCase(effectiveRemote)) {
            log.info("[WhatsAppFilter] Mensagem ignorada: grupo ou broadcast remoteJid={}", effectiveRemote);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "group-or-broadcast"));
        }

        if (!evolutionWebhookDedupService.claimDelivery(instance, messageKeyId, effectiveRemote, fromMeFlag, dedupBodySnippet)) {
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "duplicate-message-key"));
        }

        log.info("Evolution webhook messages.upsert: instance='{}' remoteJid='{}'", instance, effectiveRemote);
        EvolutionIncomingMessageDTO incoming = mapIncoming(data, payload);
        if (incoming.getFromJid() == null || incoming.getFromJid().isBlank()) {
            log.warn("Evolution webhook ignorado: payload sem remoteJid util (instance={}). data.hasKey={} data.hasMessage={}",
                instance, data.has("key"), data.has("message"));
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "empty-payload"));
        }

        // Ordem: instância Evolution primeiro (dono da conta no app). Antes, findByIncomingNumber + auto-provision
        // faziam um contacto (outro MSISDN) mapear para outro Usuario e o bot respondia nessa conversa.
        WhatsAppWebhookPolicyService.TenantResolution resolution = WhatsAppWebhookPolicyService.TenantResolution.UNKNOWN;
        Long userId = null;
        try {
            if (instance != null && !instance.isBlank()) {
                Optional<Long> tenantOpt = aiProvidersConfigService.resolveUsuarioIdByEvolutionInstance(instance);
                if (tenantOpt.isPresent()) {
                    userId = tenantOpt.get();
                    resolution = WhatsAppWebhookPolicyService.TenantResolution.BY_EVOLUTION_INSTANCE;
                }
            }
            if (userId == null) {
                Optional<Usuario> ou = whatsAppUserMappingService.findByIncomingNumber(incoming.getFromJid());
                if (ou.isPresent()) {
                    userId = ou.get().getId();
                    resolution = WhatsAppWebhookPolicyService.TenantResolution.BY_WHATSAPP_LINK;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Evolution webhook: lookup tenant falhou (jid={}): {}", incoming.getFromJid(), ex.getMessage());
        }
        if (userId == null) {
            Optional<Long> provisioned = whatsappAccountProvisioner.provisionFromWhatsAppJid(incoming.getFromJid());
            if (provisioned.isPresent()) {
                userId = provisioned.get();
                resolution = WhatsAppWebhookPolicyService.TenantResolution.BY_AUTO_PROVISION;
            }
        }
        if (userId == null) {
            log.info("Evolution webhook ignorado: remoteJid='{}' sem usuario resolvido (instancia '{}')",
                incoming.getFromJid(), instance);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "unknown-user-or-instance"));
        }

        if (!whatsAppWebhookPolicyService.allowsResolvedTenant(resolution, userId)) {
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "only-respond-owner-no-explicit-phone"));
        }

        String ignoreReason = shouldIgnoreReason(incoming, userId);
        if (ignoreReason != null) {
            log.info("Evolution webhook ignorado: instance={} remoteJid={} fromMe={} mediaType={} reason={}",
                instance, incoming.getFromJid(), incoming.isFromMe(), incoming.getMediaType(), ignoreReason);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", ignoreReason));
        }
        whatsAppUserMappingService.ensureLinkedIfEmpty(userId, incoming.getFromJid());
        whatsAppCommandService.sendJarvisInstantAck(incoming, userId, instance);
        log.info("Evolution webhook enfileirado (async): userId={} remoteJid={} fromMe={} msgKey={}", userId, incoming.getFromJid(), incoming.isFromMe(), incoming.getMessageKeyId());
        evolutionWebhookAsyncProcessor.processEvolutionMessageAsync(incoming, userId, instance);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /** Resolve remoteJid tal como na mensagem Baileys (inclui escape @lid). */
    private static String extractEffectiveRemoteJid(JsonNode key, JsonNode payloadRoot) {
        String fromJid = key.path("remoteJid").asText("");
        if (fromJid.contains("@lid")) {
            String alt = key.path("remoteJidAlt").asText("");
            if (alt != null && !alt.isBlank()) {
                fromJid = alt;
            } else {
                String sender = payloadRoot.path("sender").asText("");
                if (sender.isBlank()) {
                    sender = payloadRoot.path("body").path("sender").asText("");
                }
                if (sender != null && !sender.isBlank()) {
                    if (sender.contains("@s.whatsapp.net")) {
                        fromJid = sender;
                    } else if (sender.replaceAll("\\D", "").length() >= 10) {
                        fromJid = sender.replaceAll("\\D", "") + "@s.whatsapp.net";
                    }
                }
            }
        }
        return fromJid;
    }

    private EvolutionIncomingMessageDTO mapIncoming(JsonNode data, JsonNode payloadRoot) {
        JsonNode message = unwrapInnerMessage(data.path("message"));
        JsonNode key = data.path("key");
        String fromJid = extractEffectiveRemoteJid(key, payloadRoot);
        boolean fromMe = key.path("fromMe").asBoolean(false);
        String messageKeyId = key.path("id").asText("");

        String text = message.path("conversation").asText("");
        if (text.isBlank()) {
            text = message.path("extendedTextMessage").path("text").asText("");
        }
        if (text.isBlank()) {
            text = message.path("documentMessage").path("caption").asText("");
        }
        if (text.isBlank()) {
            text = message.path("imageMessage").path("caption").asText("");
        }

        String mediaType = null;
        String mime = null;
        byte[] mediaBytes = null;
        String mediaUrl = null;

        if (message.has("audioMessage")) {
            JsonNode audio = message.path("audioMessage");
            mediaType = "audio";
            mime = audio.path("mimetype").asText("audio/ogg");
            // Evolution (webhook com base64) grava o buffer em message.base64, não dentro de audioMessage
            mediaBytes = decodeBase64Safe(firstNonBlank(
                audio.path("base64").asText(""),
                message.path("base64").asText(""),
                data.path("base64").asText("")
            ));
            mediaUrl = firstNonBlank(audio.path("mediaUrl").asText(""), audio.path("url").asText(""));
        } else if (message.has("imageMessage")) {
            JsonNode image = message.path("imageMessage");
            mediaType = "image";
            mime = image.path("mimetype").asText("image/jpeg");
            mediaBytes = decodeBase64Safe(firstNonBlank(
                image.path("base64").asText(""),
                message.path("base64").asText(""),
                data.path("base64").asText("")
            ));
            mediaUrl = firstNonBlank(image.path("mediaUrl").asText(""), image.path("url").asText(""));
        } else if (message.has("documentMessage")) {
            JsonNode document = message.path("documentMessage");
            mediaType = "document";
            mime = document.path("mimetype").asText("application/pdf");
            mediaBytes = decodeBase64Safe(firstNonBlank(
                document.path("base64").asText(""),
                message.path("base64").asText(""),
                data.path("base64").asText("")
            ));
            mediaUrl = firstNonBlank(document.path("mediaUrl").asText(""), document.path("url").asText(""));
        }

        return new EvolutionIncomingMessageDTO(fromJid, fromMe, text, mediaBytes, mime, mediaType, mediaUrl, messageKeyId, false);
    }

    /**
     * Baileys pode envolver texto/mídia em ephemeralMessage ou viewOnceMessage.
     * Primeira linha de texto do payload (dedupe quando o Evolution omite {@code key.id}).
     */
    private static String extractTextPreviewForDedup(JsonNode data) {
        if (data == null || !data.has("message")) {
            return "";
        }
        try {
            JsonNode message = unwrapInnerMessage(data.path("message"));
            String text = message.path("conversation").asText("");
            if (text.isBlank()) {
                text = message.path("extendedTextMessage").path("text").asText("");
            }
            if (text.isBlank()) {
                text = message.path("documentMessage").path("caption").asText("");
            }
            if (text.isBlank()) {
                text = message.path("imageMessage").path("caption").asText("");
            }
            return text;
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonNode unwrapInnerMessage(JsonNode message) {
        if (message == null || message.isMissingNode()) {
            return message;
        }
        if (message.has("ephemeralMessage") && message.path("ephemeralMessage").has("message")) {
            return unwrapInnerMessage(message.path("ephemeralMessage").path("message"));
        }
        if (message.has("viewOnceMessage") && message.path("viewOnceMessage").has("message")) {
            return unwrapInnerMessage(message.path("viewOnceMessage").path("message"));
        }
        if (message.has("viewOnceMessageV2") && message.path("viewOnceMessageV2").has("message")) {
            return unwrapInnerMessage(message.path("viewOnceMessageV2").path("message"));
        }
        return message;
    }

    /**
     * @return motivo curto para ignorar, ou {@code null} se deve processar
     */
    private String shouldIgnoreReason(EvolutionIncomingMessageDTO incoming, Long userId) {
        String fromJid = incoming.getFromJid();

        if (fromJid.endsWith("@g.us") || fromJid.endsWith("@broadcast") || fromJid.equalsIgnoreCase("status@broadcast")) {
            return "group-or-broadcast";
        }

        String texto = incoming.getText();
        if (texto != null && !texto.isBlank() && jarvisProtocolService.isProceduralAckEcho(texto)) {
            return "jarvis-procedural-echo";
        }
        if (texto != null && !texto.isBlank() && jarvisProtocolService.isOurSignedStackMessage(texto)) {
            return "jarvis-signed-stack-message";
        }

        if (!whatsAppBotAllowlist.isEvolutionSelfChatThread(fromJid, userId)) {
            log.warn("[WhatsAppFilter] Mensagem ignorada: só na conversa com o próprio número (remoteJid={}, fromMe={}, userId={}). "
                    + "Confirma o chat 'mensagens para ti' e +55 no perfil ou dono WhatsApp na IA.",
                fromJid, incoming.isFromMe(), userId);
            return "not-self-chat-thread";
        }

        boolean hasText = incoming.getText() != null && !incoming.getText().isBlank();
        boolean hasSupportedMedia = incoming.getMediaType() != null
            && ("audio".equalsIgnoreCase(incoming.getMediaType())
                || "image".equalsIgnoreCase(incoming.getMediaType())
                || "document".equalsIgnoreCase(incoming.getMediaType()));
        if (!hasText && !hasSupportedMedia) {
            return "no-text-and-no-audio-image";
        }
        return null;
    }

    private static boolean isMessagesUpsertEvent(String event) {
        if (event == null || event.isBlank()) {
            return false;
        }
        String e = event.trim();
        return "messages.upsert".equalsIgnoreCase(e)
            || "MESSAGES_UPSERT".equals(e)
            || "messages-upsert".equalsIgnoreCase(e)
            || "send.message".equalsIgnoreCase(e);
    }

    /**
     * {@code data} pode ser o objeto da mensagem, um array, ou {@code { messages: [ ... ] }}.
     */
    private static JsonNode normalizeWebhookData(JsonNode payload) {
        JsonNode data = pickDataNode(payload);
        if (data != null && data.isObject() && data.has("key") && data.has("message")) {
            return data;
        }
        if (data == null || data.isMissingNode() || data.isNull()) {
            return data;
        }
        if (data.isArray() && data.size() > 0) {
            return data.get(0);
        }
        JsonNode messages = data.path("messages");
        if (messages.isArray() && messages.size() > 0) {
            return messages.get(0);
        }
        return data;
    }

    /** {@code data} na raiz ou dentro de {@code body} (proxies / formatos da Evolution). */
    private static JsonNode pickDataNode(JsonNode payload) {
        JsonNode root = payload.path("data");
        if (looksLikeMessageBlock(root)) {
            return root;
        }
        JsonNode bodyData = payload.path("body").path("data");
        if (looksLikeMessageBlock(bodyData)) {
            return bodyData;
        }
        if (root != null && !root.isMissingNode() && !root.isNull()) {
            return root;
        }
        return bodyData;
    }

    private static boolean looksLikeMessageBlock(JsonNode n) {
        return n != null && !n.isMissingNode() && !n.isNull() && n.isObject()
            && ((n.has("key") && n.has("message")) || n.has("messages"));
    }

    private byte[] decodeBase64Safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String normalized = value.contains(",") ? value.substring(value.indexOf(',') + 1) : value;
            return Base64.getDecoder().decode(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return c != null ? c : "";
    }

    private static String firstNonBlank(String a, String b, String c, String d) {
        String x = firstNonBlank(a, b, c);
        if (x != null && !x.isBlank()) {
            return x;
        }
        return d != null ? d : "";
    }
}
