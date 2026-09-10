package com.consumoesperto.edith;

import java.util.regex.Pattern;

/**
 * Sanitiza texto de origem do usuário (digitado ou importado de PDF/CSV)
 * antes de sair em capability. Não é parser de intenção.
 */
public final class PromptSanitize {

    private static final Pattern DELIMS = Pattern.compile(
        "(?i)```|<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>|<\\|user\\|>|<\\|assistant\\|>"
            + "|</s>|<s>|\\[/INST\\]|\\[INST\\]|<</SYS>>|<<SYS>>"
    );

    private PromptSanitize() {
    }

    public static String userText(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' ');
                continue;
            }
            if (Character.isISOControl(c) || c == '\uFEFF' || c == '\u200B' || c == '\u2028' || c == '\u2029') {
                continue;
            }
            sb.append(c);
        }
        String cleaned = DELIMS.matcher(sb.toString()).replaceAll(" ");
        return cleaned.replaceAll(" {2,}", " ").trim();
    }

    public static String userText(String raw, int maxChars) {
        String s = userText(raw);
        if (maxChars > 0 && s.length() > maxChars) {
            return s.substring(0, maxChars);
        }
        return s;
    }
}
