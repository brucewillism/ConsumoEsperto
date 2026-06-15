package com.consumoesperto.model;

/**
 * Modelo de renda do utilizador — define UI, cálculo e contexto J.A.R.V.I.S.
 */
public enum TipoConfiguracaoRenda {
    /** CLT / concursado: holerite, descontos e contracheque PDF. */
    CONTRACHEQUE,
    /** Autônomo recorrente: um valor fixo mensal sem holerite. */
    RECEBIMENTO_UNICO,
    /** Renda variável: estimativa pela média móvel de receitas (30 dias). */
    FLUXO_DIARIO
}
