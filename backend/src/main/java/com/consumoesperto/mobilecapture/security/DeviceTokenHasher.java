package com.consumoesperto.mobilecapture.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class DeviceTokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceTokenHasher() {
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "ce_mcd_" + HexFormat.of().formatHex(bytes);
    }

    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token vazio");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public static String fingerprintPrefix(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "????";
        }
        return fingerprint.substring(0, 8);
    }
}
