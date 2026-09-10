package com.consumoesperto.ingest.notificacao.parser;

/**
 * Resultado do parser de notificação bancária.
 *
 * {@code destino}: IGNORAR, ESTORNO, LANCAR, NAO_RECONHECIDA.
 */
public record ParsedNotificacaoBancaria(
    String destino,
    java.math.BigDecimal valor,
    String estabelecimento,
    String estabelecimentoNormalizado,
    String tipo,
    String canal,
    String tipoTransacao,
    boolean confiante,
    String motivo
) {
    public static ParsedNotificacaoBancaria ignorar(String motivo) {
        return new ParsedNotificacaoBancaria("IGNORAR", null, null, null, null, null, null, true, motivo);
    }

    public static ParsedNotificacaoBancaria naoReconhecida(String motivo) {
        return new ParsedNotificacaoBancaria("NAO_RECONHECIDA", null, null, null, null, null, null, false, motivo);
    }

    public static ParsedNotificacaoBancaria estorno(
        java.math.BigDecimal valor, String estabelecimento, String estabelecimentoNormalizado, String canal
    ) {
        return new ParsedNotificacaoBancaria(
            "ESTORNO", valor, estabelecimento, estabelecimentoNormalizado, "ESTORNO", canal, "DESPESA", true, "estorno");
    }

    public static ParsedNotificacaoBancaria lancar(
        java.math.BigDecimal valor,
        String estabelecimento,
        String estabelecimentoNormalizado,
        String tipo,
        String canal,
        String tipoTransacao,
        boolean confiante
    ) {
        return new ParsedNotificacaoBancaria(
            "LANCAR", valor, estabelecimento, estabelecimentoNormalizado, tipo, canal, tipoTransacao, confiante, null);
    }
}
