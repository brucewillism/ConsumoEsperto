package com.consumoesperto.model;

/**
 * Semântica da linha de extrato — não confundir sinal do valor com tipo de movimento.
 */
public enum ImportedRowKind {
    DESPESA,
    RECEITA,
    PAGAMENTO_FATURA,
    ESTORNO,
    TRANSFERENCIA
}
