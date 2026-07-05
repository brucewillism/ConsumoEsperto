package com.consumoesperto.service;

import com.consumoesperto.model.MovimentacaoSaldoLog.OrigemMovimentacaoSaldo;

/**
 * Origem da mutação de saldo em curso (audit trail). Default {@code APP};
 * pontos de entrada não-app (webhook WhatsApp, jobs, reparo, importação) definem
 * explicitamente com try/finally.
 */
public final class SaldoMovimentacaoContexto {

    private static final ThreadLocal<OrigemMovimentacaoSaldo> ORIGEM = new ThreadLocal<>();

    private SaldoMovimentacaoContexto() {}

    public static void definirOrigem(OrigemMovimentacaoSaldo origem) {
        if (origem != null) {
            ORIGEM.set(origem);
        }
    }

    public static OrigemMovimentacaoSaldo origemAtual() {
        OrigemMovimentacaoSaldo o = ORIGEM.get();
        return o != null ? o : OrigemMovimentacaoSaldo.APP;
    }

    public static void limpar() {
        ORIGEM.remove();
    }
}
