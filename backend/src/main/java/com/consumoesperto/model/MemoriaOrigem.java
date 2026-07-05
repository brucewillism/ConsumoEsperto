package com.consumoesperto.model;

/**
 * De onde a memória veio — controla a confiança inicial e os guardrails de captura.
 */
public enum MemoriaOrigem {
    /** Anotação explícita do usuário no WhatsApp («Jarvis, anote isso»). */
    USUARIO_WHATSAPP,
    /** Anotação explícita via app/chat do dashboard. */
    USUARIO_APP,
    /** Extraída automaticamente de conversa ou padrão detectado. */
    INFERIDO,
    /** Gerada por job (resumo mensal, consolidação, digest). */
    SISTEMA
}
