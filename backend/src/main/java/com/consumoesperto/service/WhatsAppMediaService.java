package com.consumoesperto.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Download de mídia WhatsApp (Evolution API em 2 passos ou Twilio legado).
 */
@Service
@RequiredArgsConstructor
public class WhatsAppMediaService {

    private final EvolutionMediaService evolutionMediaService;
    private final TwilioWhatsAppService twilioWhatsAppService;

    /**
     * Evolution: desencripta áudio via {@code getBase64FromMediaMessage} quando o webhook não traz base64.
     */
    public byte[] downloadEvolutionAudio(String instanceName, String remoteJid, String messageId,
                                         boolean fromMe, String apiKeyOverride) {
        return evolutionMediaService.fetchBase64FromMediaMessage(
            instanceName, remoteJid, messageId, fromMe, apiKeyOverride);
    }

    /** Evolution / URL temporária com header apikey. */
    public byte[] downloadFromUrl(String mediaUrl, String apiKeyOverride) {
        return evolutionMediaService.fetchMedia(mediaUrl, apiKeyOverride);
    }

    /** Twilio: GET em {@code MediaUrl0}. */
    public byte[] downloadTwilioMedia(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }
        return twilioWhatsAppService.downloadMedia(mediaUrl);
    }
}
