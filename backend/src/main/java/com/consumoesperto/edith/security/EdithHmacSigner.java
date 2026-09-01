package com.consumoesperto.edith.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC do Tool Bridge — alinhado ao {@code io.edith.sdk.tools.EdithToolAuthenticator} 0.4.1.
 * Payload: {@code timestamp + "\n" + nonce + "\n" + requestId + "\n" + sha256Hex(body)}.
 */
public final class EdithHmacSigner {

    private EdithHmacSigner() {
    }

    public static String sha256Hex(byte[] body) {
        try {
            byte[] data = body != null ? body : new byte[0];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public static String sign(String secret, String timestamp, String nonce, String requestId, byte[] body) {
        String ts = timestamp != null ? timestamp.trim() : "";
        String n = nonce != null ? nonce : "";
        String rid = requestId != null ? requestId : "";
        String payload = ts + "\n" + n + "\n" + rid + "\n" + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    public static boolean signaturesMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] aa = expected.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] bb = actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (aa.length != bb.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aa.length; i++) {
            result |= aa[i] ^ bb[i];
        }
        return result == 0;
    }
}
