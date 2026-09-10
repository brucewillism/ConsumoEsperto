package com.consumoesperto.edith;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Campo de origem do usuário (ou de importação de terceiro) para a E.D.I.T.H.
 * Sempre {@code {"value": "...", "untrusted": true}}.
 */
public final class UntrustedText {

    private final String value;

    public UntrustedText(String value) {
        this.value = value != null ? value : "";
    }

    public static UntrustedText of(String raw) {
        return new UntrustedText(PromptSanitize.userText(raw));
    }

    public static UntrustedText of(String raw, int maxChars) {
        return new UntrustedText(PromptSanitize.userText(raw, maxChars));
    }

    @JsonProperty("value")
    public String getValue() {
        return value;
    }

    @JsonProperty("untrusted")
    public boolean isUntrusted() {
        return true;
    }
}
