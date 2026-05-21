package com.consumoesperto.model;

/**
 * Modalidade de pagamento do 13º salário (CLT).
 */
public enum TipoRecebimento13 {
    /** Pagamento integral em um único mês (ex.: novembro). */
    PARCELA_UNICA,
    /** Adiantamento (50% bruto, sem descontos) + saldo em dezembro (com retenções). */
    DUAS_PARCELAS
}
