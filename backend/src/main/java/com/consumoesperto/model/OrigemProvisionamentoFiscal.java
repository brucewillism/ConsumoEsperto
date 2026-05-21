package com.consumoesperto.model;

/**
 * Identificador estável de receitas provisionadas pelo planejamento fiscal.
 * Usado para substituir provisões ao re-sincronizar sem duplicar lançamentos.
 */
public enum OrigemProvisionamentoFiscal {
    RESTITUICAO_IR,
    DECIMO_TERCEIRO_UNICO,
    DECIMO_TERCEIRA_PRIMEIRA,
    DECIMO_TERCEIRA_SEGUNDA
}
