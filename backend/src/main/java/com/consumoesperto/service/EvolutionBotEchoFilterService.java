package com.consumoesperto.service;

import com.consumoesperto.dto.EvolutionIncomingMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evita loop de eco: webhooks {@code messages.upsert} de mensagens que o próprio bot enviou
 * (texto assinado, PDF de relatório, áudio PTT, imagem, etc.).
 * <p>
 * Na conversa consigo mesmo, mensagens do utilizador também chegam com {@code fromMe=true};
 * por isso não basta filtrar {@code fromMe} — registamos IDs/chaves e fingerprints de saída.
 */
@Service
@RequiredArgsConstructor
public class EvolutionBotEchoFilterService {

    private static final int ECHO_TTL_MINUTES = 10;
    private static final int MAX_TRACKED_KEYS = 500;
    private static final int MAX_TRACKED_MEDIA = 200;

    private final JarvisProtocolService jarvisProtocolService;
    private final EvolutionApiService evolutionApiService;

    private final Map<String, LocalDateTime> outgoingMessageKeys = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> outgoingTexts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> outgoingMediaFingerprints = new ConcurrentHashMap<>();

    /**
     * @return motivo curto para ignorar, ou {@code null} se deve processar
     */
    public String reasonToIgnore(EvolutionIncomingMessageDTO incoming) {
        if (incoming == null) {
            return null;
        }

        String textReason = reasonForTextEcho(incoming.getText());
        if (textReason != null) {
            return textReason;
        }
        if (isRecentOutgoingTextEcho(incoming.getFromJid(), incoming.getText())) {
            return "bot-outgoing-text-echo";
        }

        String messageKeyId = incoming.getMessageKeyId();
        if (messageKeyId != null && !messageKeyId.isBlank() && isKnownOutgoingKey(messageKeyId)) {
            return "bot-outgoing-message-key";
        }

        if (!incoming.isFromMe()) {
            return null;
        }

        String mediaType = incoming.getMediaType();
        if (mediaType == null || mediaType.isBlank()) {
            return null;
        }

        String mt = mediaType.trim().toLowerCase(Locale.ROOT);
        if (!"audio".equals(mt) && !"image".equals(mt) && !"document".equals(mt)) {
            return null;
        }

        if (matchesOutgoingMedia(incoming)) {
            return "bot-outgoing-media-echo";
        }

        return null;
    }

    public void registerOutgoingText(String to, String text) {
        String key = outgoingTextKey(to, text);
        if (key.isBlank()) {
            return;
        }
        outgoingTexts.put(key, LocalDateTime.now());
        prune(outgoingTexts, MAX_TRACKED_KEYS);
    }

    public boolean isRecentOutgoingTextEcho(String to, String text) {
        String key = outgoingTextKey(to, text);
        if (key.isBlank()) {
            return false;
        }
        pruneExpired(outgoingTexts);
        LocalDateTime sentAt = outgoingTexts.get(key);
        return sentAt != null && sentAt.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    public void registerOutgoingMessageKey(String messageKeyId) {
        if (messageKeyId == null || messageKeyId.isBlank()) {
            return;
        }
        outgoingMessageKeys.put(messageKeyId.trim(), LocalDateTime.now());
        prune(outgoingMessageKeys, MAX_TRACKED_KEYS);
    }

    public void registerOutgoingMedia(String to, String mediaType, byte[] bytes, String fileName) {
        if (to == null || to.isBlank() || mediaType == null || mediaType.isBlank()) {
            return;
        }
        String mt = mediaType.trim().toLowerCase(Locale.ROOT);
        String recipient = evolutionApiService.normalizeToNumber(to);
        if (recipient.isBlank()) {
            return;
        }
        String hash = bytes != null && bytes.length > 0 ? sha256Prefix(bytes) : "";
        String fn = normalizeFileName(fileName);
        String fingerprint = recipient + "|" + mt + "|" + hash + "|" + fn;
        outgoingMediaFingerprints.put(fingerprint, LocalDateTime.now());
        if (!hash.isBlank()) {
            outgoingMediaFingerprints.put(recipient + "|" + mt + "|" + hash + "|", LocalDateTime.now());
        }
        if (!fn.isBlank()) {
            outgoingMediaFingerprints.put(recipient + "|" + mt + "||" + fn, LocalDateTime.now());
        }
        prune(outgoingMediaFingerprints, MAX_TRACKED_MEDIA);
    }

    private String reasonForTextEcho(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (jarvisProtocolService.isProceduralAckEcho(text)) {
            return "jarvis-procedural-echo";
        }
        if (jarvisProtocolService.isOurSignedStackMessage(text)) {
            return "jarvis-signed-stack-message";
        }
        return null;
    }

    private boolean isKnownOutgoingKey(String messageKeyId) {
        pruneExpired(outgoingMessageKeys);
        LocalDateTime sentAt = outgoingMessageKeys.get(messageKeyId.trim());
        return sentAt != null && sentAt.isAfter(LocalDateTime.now().minusMinutes(ECHO_TTL_MINUTES));
    }

    private boolean matchesOutgoingMedia(EvolutionIncomingMessageDTO incoming) {
        pruneExpired(outgoingMediaFingerprints);
        String recipient = evolutionApiService.normalizeToNumber(incoming.getFromJid());
        if (recipient.isBlank()) {
            return false;
        }
        String mt = incoming.getMediaType().trim().toLowerCase(Locale.ROOT);
        byte[] bytes = incoming.getMediaBytes();
        String hash = bytes != null && bytes.length > 0 ? sha256Prefix(bytes) : "";
        String fn = normalizeFileName(incoming.getMediaFileName());

        if (!hash.isBlank()) {
            LocalDateTime byHash = outgoingMediaFingerprints.get(recipient + "|" + mt + "|" + hash + "|");
            if (byHash != null && byHash.isAfter(LocalDateTime.now().minusMinutes(ECHO_TTL_MINUTES))) {
                return true;
            }
            LocalDateTime byHashFn = outgoingMediaFingerprints.get(recipient + "|" + mt + "|" + hash + "|" + fn);
            if (byHashFn != null && byHashFn.isAfter(LocalDateTime.now().minusMinutes(ECHO_TTL_MINUTES))) {
                return true;
            }
        }
        if (!fn.isBlank()) {
            LocalDateTime byName = outgoingMediaFingerprints.get(recipient + "|" + mt + "||" + fn);
            if (byName != null && byName.isAfter(LocalDateTime.now().minusMinutes(ECHO_TTL_MINUTES))) {
                return true;
            }
        }
        return false;
    }

    private String outgoingTextKey(String jidOrNumber, String text) {
        if (jidOrNumber == null || jidOrNumber.isBlank() || text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return evolutionApiService.normalizeToNumber(jidOrNumber) + "|" + normalized;
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256Prefix(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception e) {
            return Integer.toHexString(bytes.length);
        }
    }

    private static void pruneExpired(Map<String, LocalDateTime> map) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ECHO_TTL_MINUTES);
        map.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    private static void prune(Map<String, LocalDateTime> map, int maxSize) {
        pruneExpired(map);
        if (map.size() <= maxSize) {
            return;
        }
        map.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(map.size() - maxSize)
            .map(Map.Entry::getKey)
            .forEach(map::remove);
    }
}
