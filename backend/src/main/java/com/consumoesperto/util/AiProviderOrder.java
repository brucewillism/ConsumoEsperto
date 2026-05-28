package com.consumoesperto.util;

import com.consumoesperto.service.AiProviderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordem canónica de fallback de IA: Groq → OpenAI → Claude → Gemini → DeepSeek → Ollama.
 */
public final class AiProviderOrder {

    public static final List<String> CANONICAL_NAMES = List.of(
        "GROQ",
        "OPENAI",
        "CLAUDE",
        "GEMINI",
        "DEEPSEEK",
        "OLLAMA"
    );

    private AiProviderOrder() {
    }

    public static List<String> canonicalNamesCopy() {
        return new ArrayList<>(CANONICAL_NAMES);
    }

    public static List<AiProviderType> canonicalTypes() {
        List<AiProviderType> out = new ArrayList<>();
        for (String name : CANONICAL_NAMES) {
            AiProviderType t = AiProviderType.fromString(name);
            if (t != null && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
