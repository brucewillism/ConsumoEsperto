package com.consumoesperto.util;

/**
 * Mensagens amigáveis quando provedores de IA falham na cadeia de fallback.
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
            || raw.contains("Nao foi possivel processar IA")
            || raw.contains("Falha OCR em todos provedores")
            || raw.contains("429 Too Many Requests")
            || raw.contains("rate_limit")
            || raw.contains("insufficient_quota")
            || raw.contains("Rate limit reached")
            || raw.contains("GEMINI_API_KEY não configurada")
            || raw.contains("CLAUDE_API_KEY não configurada")
            || raw.contains("DEEPSEEK_API_KEY não configurada");
        if (!falhaIa) {
            return null;
        }
        boolean groqLimite = raw.contains("Rate limit reached") || raw.contains("rate_limit_exceeded")
            || raw.contains("GROQ:");
        boolean openaiQuota = raw.contains("insufficient_quota") || raw.contains("OPENAI:");
        boolean semGemini = raw.contains("GEMINI: GEMINI_API_KEY não configurada")
            || raw.contains("GEMINI_API_KEY não configurada");
        boolean semClaude = raw.contains("CLAUDE: CLAUDE_API_KEY não configurada")
            || raw.contains("CLAUDE_API_KEY não configurada");
        boolean semDeepseek = raw.contains("DEEPSEEK: DEEPSEEK_API_KEY não configurada")
            || raw.contains("DEEPSEEK_API_KEY não configurada");
        boolean deepseekFalhou = raw.contains("DEEPSEEK:") && !semDeepseek;

        StringBuilder sb = new StringBuilder();
        sb.append("O serviço de IA está temporariamente indisponível");
        if (groqLimite && openaiQuota) {
            sb.append(" (limite diário da Groq e quota da OpenAI esgotados)");
        } else if (groqLimite) {
            sb.append(" (limite diário da Groq atingido)");
        } else if (openaiQuota) {
            sb.append(" (quota da OpenAI esgotada)");
        }
        sb.append(".");

        if (deepseekFalhou) {
            sb.append(" O DeepSeek também falhou — verifique saldo/chave ou tente mais tarde.");
        } else if (!semDeepseek) {
            sb.append(" Tente novamente; o sistema deve usar DeepSeek como reserva.");
        } else if (semGemini && semClaude && (groqLimite || openaiQuota)) {
            sb.append(
                " Configure no .env da VPS pelo menos uma reserva: GEMINI_API_KEY (Google AI Studio)"
                    + " ou DEEPSEEK_API_KEY, depois reinicie: docker compose up -d backend.");
        } else if (semGemini && groqLimite) {
            sb.append(" Configure GEMINI_API_KEY ou DEEPSEEK_API_KEY no .env da VPS e reinicie o backend.");
        } else if (groqLimite || openaiQuota) {
            sb.append(" Tente novamente em cerca de 1–2 horas ou ative um provedor de reserva no .env.");
        }
        return sb.toString();
    }
}
