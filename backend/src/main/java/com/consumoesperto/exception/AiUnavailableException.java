package com.consumoesperto.exception;

/**
 * Provedores de IA (Groq, OpenAI, Gemini, etc.) indisponíveis — quota, rate limit ou chaves em falta.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
