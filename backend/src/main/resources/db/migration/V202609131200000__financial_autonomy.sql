-- Autonomia financeira: preferências, evidências, outbox, decisões, revisão, lock de jobs.
-- Tabelas novas só via Flyway.

CREATE TABLE IF NOT EXISTS autonomy_preferencia (
    usuario_id              BIGINT PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    nivel                   VARCHAR(24)  NOT NULL DEFAULT 'ASSISTED',
    registrar_auto          BOOLEAN      NOT NULL DEFAULT TRUE,
    classificar_auto        BOOLEAN      NOT NULL DEFAULT TRUE,
    aprender_categorias     BOOLEAN      NOT NULL DEFAULT TRUE,
    detectar_assinaturas    BOOLEAN      NOT NULL DEFAULT TRUE,
    detectar_duplicatas     BOOLEAN      NOT NULL DEFAULT TRUE,
    detectar_anomalias      BOOLEAN      NOT NULL DEFAULT TRUE,
    prever_saldo            BOOLEAN      NOT NULL DEFAULT TRUE,
    jarvis_proativo         BOOLEAN      NOT NULL DEFAULT FALSE,
    resumo_diario           BOOLEAN      NOT NULL DEFAULT FALSE,
    resumo_semanal          BOOLEAN      NOT NULL DEFAULT FALSE,
    silencioso_inicio       TIME,
    silencioso_fim          TIME,
    atualizado_em           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transaction_evidence (
    id                 BIGSERIAL PRIMARY KEY,
    transacao_id       BIGINT       NOT NULL REFERENCES transacoes(id) ON DELETE CASCADE,
    usuario_id         BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    source             VARCHAR(40)  NOT NULL,
    external_id        VARCHAR(128),
    source_event_id    VARCHAR(128),
    confidence         NUMERIC(5,4),
    first_seen_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at       TIMESTAMP,
    metadata_min       VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS ix_tx_evidence_transacao
    ON transaction_evidence (transacao_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tx_evidence_source_ext
    ON transaction_evidence (usuario_id, source, external_id)
    WHERE external_id IS NOT NULL AND btrim(external_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_tx_evidence_tx_source
    ON transaction_evidence (transacao_id, source);

CREATE TABLE IF NOT EXISTS financial_domain_event (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    event_type     VARCHAR(48)  NOT NULL,
    aggregate_id   BIGINT,
    payload_min    VARCHAR(500),
    processed      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_fin_domain_event_pendente
    ON financial_domain_event (processed, created_at)
    WHERE processed = FALSE;

CREATE TABLE IF NOT EXISTS autonomy_decision_log (
    id               BIGSERIAL PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    event_id         BIGINT REFERENCES financial_domain_event(id) ON DELETE SET NULL,
    decision_type    VARCHAR(48)  NOT NULL,
    policy           VARCHAR(32)  NOT NULL,
    confidence       NUMERIC(5,4),
    action           VARCHAR(64)  NOT NULL,
    result           VARCHAR(32)  NOT NULL,
    rule_code        VARCHAR(80),
    reason           VARCHAR(400),
    cognitive_used   BOOLEAN      NOT NULL DEFAULT FALSE,
    edith_task_id    VARCHAR(80),
    transacao_id     BIGINT REFERENCES transacoes(id) ON DELETE SET NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_autonomy_decision_usuario
    ON autonomy_decision_log (usuario_id, created_at DESC);

CREATE TABLE IF NOT EXISTS autonomy_review_item (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    kind           VARCHAR(32)  NOT NULL,
    transacao_id   BIGINT REFERENCES transacoes(id) ON DELETE SET NULL,
    title          VARCHAR(200) NOT NULL,
    detail         VARCHAR(500),
    confidence     NUMERIC(5,4),
    status         VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_autonomy_review_open
    ON autonomy_review_item (usuario_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS autonomy_job_lock (
    job_name       VARCHAR(64) PRIMARY KEY,
    locked_until   TIMESTAMP   NOT NULL,
    locked_by      VARCHAR(64)
);
