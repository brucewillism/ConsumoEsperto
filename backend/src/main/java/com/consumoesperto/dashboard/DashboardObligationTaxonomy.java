package com.consumoesperto.dashboard;

import com.consumoesperto.model.Transacao;

/**
 * Taxonomia disjunta de obrigações (R1). Cada transação cai em exactamente um balde.
 */
public final class DashboardObligationTaxonomy {

    public enum Bucket {
        CARTAO_FATURA,
        EMPRESTIMO,
        FIXA_ASSINATURA_AGENDAMENTO
    }

    private DashboardObligationTaxonomy() {}

    public static Bucket of(Transacao t) {
        if (t == null) {
            return Bucket.FIXA_ASSINATURA_AGENDAMENTO;
        }
        if (t.getEmprestimoId() != null && !t.getEmprestimoId().isBlank()) {
            return Bucket.EMPRESTIMO;
        }
        if (t.getFatura() != null
            || (t.getGrupoParcelaId() != null && !t.getGrupoParcelaId().isBlank())) {
            return Bucket.CARTAO_FATURA;
        }
        return Bucket.FIXA_ASSINATURA_AGENDAMENTO;
    }
}
