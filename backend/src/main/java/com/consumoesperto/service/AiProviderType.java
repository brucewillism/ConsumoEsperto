package com.consumoesperto.service;

import java.util.Locale;

public enum AiProviderType {
    GROQ,
    GEMINI,
    OPENAI,
    OLLAMA;

    public static AiProviderType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AiProviderType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
