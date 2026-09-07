package com.consumoesperto.service.importacao.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class CsvEncodingDetector {

    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private CsvEncodingDetector() {}

    public static Result detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("O arquivo CSV está vazio.");
        }
        if (bytes.length >= 2) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) {
                throw new IllegalArgumentException("Encoding UTF-16 LE não é suportado. Exporte o CSV em UTF-8.");
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                throw new IllegalArgumentException("Encoding UTF-16 BE não é suportado. Exporte o CSV em UTF-8.");
            }
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
            return new Result("UTF-8-BOM", text, true);
        }
        try {
            String text = decodeStrict(bytes, StandardCharsets.UTF_8);
            return new Result("UTF-8", text, false);
        } catch (CharacterCodingException utf8Fail) {
            try {
                String text = decodeStrict(bytes, WINDOWS_1252);
                if (pareceBinario(text)) {
                    throw new IllegalArgumentException("Não foi possível ler o arquivo como texto CSV (encoding inválido).");
                }
                return new Result("windows-1252", text, false);
            } catch (CharacterCodingException isoFail) {
                throw new IllegalArgumentException("Não foi possível ler o arquivo como texto CSV (encoding inválido).");
            }
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static boolean pareceBinario(String text) {
        int control = 0;
        int checked = Math.min(text.length(), 4000);
        for (int i = 0; i < checked; i++) {
            char c = text.charAt(i);
            if (c == 0 || (c < 0x09) || (c > 0x0D && c < 0x20 && c != 0x1B)) {
                control++;
            }
        }
        return checked > 0 && control * 20 > checked;
    }

    public record Result(String name, String text, boolean bom) {}
}
