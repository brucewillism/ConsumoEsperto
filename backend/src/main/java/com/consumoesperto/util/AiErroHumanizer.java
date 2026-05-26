package com.consumoesperto.util;

/**
 * Mensagens amigáveis quando provedores de IA (Groq, OpenAI, Gemini, Ollama) falham.
 */
public final class AiErroHumanizer {

    private AiErroHumanizer() {
    }

    /**
     * @return texto para o utilizador ou {@code null} se não for falha de IA reconhecida
     */
    public static String humanizar(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        boolean falhaIa = raw.contains("Nao foi possivel gerar JSON via IA")
            || raw.contains("Falha OCR em todos provedores")
            || raw.contains("429 Too Many Requests")
            || raw.contains("rate_limit")
            || raw.contains("insufficient_quota")
            || raw.contains("Rate limit reached")
            || raw.contains("GEMINI_API_KEY não configurada");
        if (!falhaIa) {
            return null;
        }
        boolean semGemini = !raw.contains("GEMINI:");
        boolean groqLimite = raw.contains("Rate limit reached") || raw.contains("rate_limit_exceeded");
        boolean openaiQuota = raw.contains("insufficient_quota");
        StringBuilder sb = new StringBuilder();
        sb.append("O serviço de IA está temporariamente indisponível");
        if (groqLimite && openaiQuota) {
            sb.append(" (limite diário da Groq e quota da OpenAI esgotados)");
        } else if (groqLimite) {
            sb.append(" (limite diário da Groq atingido — costuma libertar em cerca de 1–2 horas)");
        } else if (openaiQuota) {
            sb.append(" (quota da OpenAI esgotada)");
        }
        sb.append(".");
        if (raw.contains("Nao foi possivel gerar JSON via IA")) {
            sb.append(" Tente novamente mais tarde ou peça ao administrador para configurar GEMINI_API_KEY no servidor.");
        } else if (semGemini && raw.contains("GEMINI_API_KEY")) {
            sb.append(" Configure GEMINI_API_KEY no servidor para fallback automático.");
        }
        return sb.toString();
    }
}
