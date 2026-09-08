package com.consumoesperto.eco;

import java.security.SecureRandom;

/**
 * ULID Crockford (26 chars) com prefixo do contrato. Sem dependência extra.
 */
public final class EcoUlid {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private EcoUlid() {
    }

    public static String ulid() {
        byte[] entropy = new byte[10];
        RNG.nextBytes(entropy);
        long time = System.currentTimeMillis();
        char[] out = new char[26];
        encodeTime(time, out);
        encodeEntropy(entropy, out);
        return new String(out);
    }

    public static String trace() {
        return "tr_" + ulid();
    }

    public static String span() {
        return "sp_" + ulid();
    }

    public static String toolCall() {
        return "tool_" + ulid();
    }

    public static String user(Long usuarioId) {
        return usuarioId == null ? "" : "usr_" + usuarioId;
    }

    public static boolean looksLikeId(String raw) {
        return raw != null && !raw.isBlank();
    }

    private static void encodeTime(long time, char[] out) {
        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET[(int) (time & 31)];
            time >>>= 5;
        }
    }

    private static void encodeEntropy(byte[] entropy, char[] out) {
        long acc = 0;
        int bits = 0;
        int idx = 10;
        for (byte b : entropy) {
            acc = (acc << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5 && idx < 26) {
                bits -= 5;
                out[idx++] = ALPHABET[(int) ((acc >>> bits) & 31)];
            }
        }
        if (idx < 26 && bits > 0) {
            out[idx] = ALPHABET[(int) ((acc << (5 - bits)) & 31)];
        }
    }
}
