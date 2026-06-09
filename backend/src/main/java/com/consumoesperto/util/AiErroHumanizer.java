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
        if (raw.contains("Transaction timed out")) {
            return "O processamento do documento demorou mais que o tempo permitido. "
                + "Tente enviar o PDF novamente em alguns instantes.";
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
            || raw.contains("DEEPSEEK_API_KEY não configurada")
            || raw.contains("SocketTimeoutException")
            || raw.contains("Connect timed out")
            || raw.contains("Read timed out")
            || raw.contains("Connection refused")
            || raw.contains("OLLAMA:");
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
        boolean ollamaFalhou = raw.contains("OLLAMA:");
        boolean ollamaConn = ollamaFalhou && (raw.contains("Connection refused")
            || raw.contains("Failed to connect") || raw.contains("connect timed out"));
        boolean ollamaModelo = ollamaFalhou && (raw.contains("not found") || raw.contains("model"));

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
        }
        if (ollamaFalhou) {
            sb.append(" O Ollama (último fallback) não respondeu");
            if (ollamaConn) {
                sb.append(": contentor parado ou URL errada (use OLLAMA_BASE_URL=http://ollama:11434 no Docker).");
            } else if (ollamaModelo) {
                sb.append(": modelo não instalado — na VPS: docker exec consumo_ollama ollama pull llama3.2");
            } else {
                sb.append(" (PDF grande pode exceder capacidade do modelo local).");
            }
        } else if (semGemini && (groqLimite || openaiQuota || deepseekFalhou)) {
            sb.append(
                " Configure GEMINI_API_KEY no .env (Google AI Studio) para reserva na nuvem antes do Ollama.");
        }
        if (groqLimite || openaiQuota) {
            if (!ollamaFalhou && !deepseekFalhou) {
                sb.append(" Tente novamente em cerca de 1–2 horas.");
            }
        }
        return sb.toString();
    }
}
