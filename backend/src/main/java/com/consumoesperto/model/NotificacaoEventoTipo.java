package com.consumoesperto.model;

/**
 * Tipos de evento mapeados para categoria e preferências do utilizador.
 */
public enum NotificacaoEventoTipo {

    SALDO_NEGATIVO_PREVISTO(NotificacaoCategoria.CRITICA, JarvisTipoNotificacaoProativa.ALERTA_RISCO_REATIVO),
    ORCAMENTO_ULTRAPASSADO(NotificacaoCategoria.CRITICA, null),
    ORCAMENTO_LIMITE(NotificacaoCategoria.IMPORTANTE, null),
    VENCIMENTO_HOJE(NotificacaoCategoria.CRITICA, JarvisTipoNotificacaoProativa.RECORRENCIAS_VENCIMENTO),

    ASSINATURA_PROXIMA(NotificacaoCategoria.IMPORTANTE, JarvisTipoNotificacaoProativa.RECORRENCIAS_VENCIMENTO),
    META_EM_RISCO(NotificacaoCategoria.IMPORTANTE, null),
    DIVIDA_RELEVANTE(NotificacaoCategoria.IMPORTANTE, JarvisTipoNotificacaoProativa.AMORTIZACAO_SAZONAL),
    AUDITORIA_JUROS(NotificacaoCategoria.IMPORTANTE, JarvisTipoNotificacaoProativa.ALERTA_RISCO_REATIVO),
    LIQUIDEZ_PARADA(NotificacaoCategoria.IMPORTANTE, JarvisTipoNotificacaoProativa.RECORRENCIAS_VENCIMENTO),

    SCORE_MENSAL(NotificacaoCategoria.INFORMATIVA, JarvisTipoNotificacaoProativa.RELATORIO_MENSAL_SCORE),
    RESUMO_SEMANAL(NotificacaoCategoria.INFORMATIVA, JarvisTipoNotificacaoProativa.RESUMO_SEMANAL),
    FORECAST_MENSAL(NotificacaoCategoria.INFORMATIVA, JarvisTipoNotificacaoProativa.DIGEST_MENSAL_SENTINELA),
    INSIGHT_MEMORIA(NotificacaoCategoria.INFORMATIVA, null),
    SENTINELA_DIA5(NotificacaoCategoria.INFORMATIVA, JarvisTipoNotificacaoProativa.SENTINELA_DIA5),
    CONFERENCIA_NOTAS(NotificacaoCategoria.INFORMATIVA, JarvisTipoNotificacaoProativa.CONFERENCIA_NOTAS),
    MODO_VIAGEM(NotificacaoCategoria.INFORMATIVA, JarvisTipoNotificacaoProativa.MODO_VIAGEM_CRONOS),

    GENERICO(NotificacaoCategoria.INFORMATIVA, null);

    private final NotificacaoCategoria categoria;
    private final JarvisTipoNotificacaoProativa preferenciaJarvis;

    NotificacaoEventoTipo(NotificacaoCategoria categoria, JarvisTipoNotificacaoProativa preferenciaJarvis) {
        this.categoria = categoria;
        this.preferenciaJarvis = preferenciaJarvis;
    }

    public NotificacaoCategoria categoria() {
        return categoria;
    }

    public JarvisTipoNotificacaoProativa preferenciaJarvis() {
        return preferenciaJarvis;
    }

    public static NotificacaoEventoTipo fromJarvisTipo(JarvisTipoNotificacaoProativa tipo) {
        if (tipo == null) {
            return GENERICO;
        }
        return switch (tipo) {
            case ALERTA_RISCO_REATIVO -> SALDO_NEGATIVO_PREVISTO;
            case RECORRENCIAS_VENCIMENTO -> ASSINATURA_PROXIMA;
            case RESUMO_SEMANAL -> RESUMO_SEMANAL;
            case RELATORIO_MENSAL_SCORE -> SCORE_MENSAL;
            case DIGEST_MENSAL_SENTINELA -> FORECAST_MENSAL;
            case SENTINELA_DIA5 -> SENTINELA_DIA5;
            case AMORTIZACAO_SAZONAL -> DIVIDA_RELEVANTE;
            case CONFERENCIA_NOTAS -> CONFERENCIA_NOTAS;
            case MODO_VIAGEM_CRONOS -> MODO_VIAGEM;
        };
    }
}
