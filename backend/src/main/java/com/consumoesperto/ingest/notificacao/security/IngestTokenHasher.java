package com.consumoesperto.ingest.notificacao.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class IngestTokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    public static final String PREFIX = "ce_ing_";

    private IngestTokenHasher() {
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
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

    public static String prefixo(String rawToken) {
        if (rawToken == null || rawToken.length() < 12) {
            return "ce_ing_****";
        }
        return rawToken.substring(0, 12);
    }
}
