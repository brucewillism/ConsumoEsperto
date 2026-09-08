package com.consumoesperto.edith;

/**
 * {@code p95_latency_ms} do manifesto. Fonte: Postgres (embedded 16, planejador real)
 * em {@code ToolPostgresBaselineTest} / {@code docs/BASELINE_TOOLS.md}.
 * As três tools da Fase 0 foram remedidas. As demais estão implementadas e no
 * manifesto, mas o p95 ainda não tem amostra Postgres dedicada — ver o relatório.
 */
public final class CapabilityLatencyManifest {

    private CapabilityLatencyManifest() {
    }

    /** Postgres p95 @ 2.500 txs, 40 amostras (2026-09-07). */
    public static final int ACCOUNTS_LIST = 3;
    public static final int TRANSACTIONS_SEARCH = 20;
    public static final int INVOICE_READ = 15;

    /**
     * Sem amostra Postgres dedicada nesta rodada. Número conservador (não usar
     * para decidir viabilidade de rota na E.D.I.T.H. até remedir).
     */
    public static final int CARDS_LIST = 15;
    public static final int MONTH_SUMMARY = 25;
    public static final int SUBSCRIPTIONS_LIST = 15;
    public static final int RECURRING_LIST = 15;
    public static final int CATEGORY_SUMMARY = 40;
    public static final int CASHFLOW_PROJECT = 40;
}
