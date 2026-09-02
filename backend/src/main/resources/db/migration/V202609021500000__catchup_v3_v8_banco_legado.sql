-- Catch-up para bancos com flyway_schema_history legado (versoes 1..23 + timestamps ate 202605021400000).
--
-- Nesses bancos o Flyway considera V1..V8 como ja aplicadas (a versao mais alta do historico e muito
-- superior) e, com out-of-order=false, ignora-as em silencio. O schema fica sem usuarios.role,
-- sem as tabelas edith_*/mobile_capture_* e sem as colunas de ingestao em transacoes, o que quebra
-- qualquer SELECT da entidade Transacao com SQLGrammarException.
--
-- A versao deste script fica acima do maior registro legado para ser sempre elegivel. Todo o DDL e
-- idempotente, portanto em bancos criados a partir da baseline V1 este script e um no-op.

-- ---------------------------------------------------------------------------
-- V4 — papel de autorizacao do usuario
-- ---------------------------------------------------------------------------
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';

-- ---------------------------------------------------------------------------
-- V5 — papel do membro no grupo familiar
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.grupo_familiar_membros') IS NOT NULL THEN
        ALTER TABLE grupo_familiar_membros
            ADD COLUMN IF NOT EXISTS papel varchar(16) NOT NULL DEFAULT 'MEMBER';

        IF to_regclass('public.grupos_familiares') IS NOT NULL THEN
            UPDATE grupo_familiar_membros m
            SET papel = 'OWNER'
            FROM grupos_familiares g
            WHERE m.grupo_familiar_id = g.id
              AND m.usuario_id = g.criador_usuario_id
              AND m.papel <> 'OWNER';
        END IF;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- V6 — idempotencia de agendamentos por competencia
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.agendamentos_pagamentos') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS agendamento_execucoes (
            id BIGSERIAL PRIMARY KEY,
            agendamento_id BIGINT NOT NULL REFERENCES agendamentos_pagamentos (id) ON DELETE CASCADE,
            data_execucao DATE NOT NULL,
            tipo_execucao VARCHAR(16) NOT NULL DEFAULT 'AUTOMATICA',
            criado_em TIMESTAMP NOT NULL DEFAULT now()
        );

        CREATE UNIQUE INDEX IF NOT EXISTS ux_agendamento_execucoes_competencia
            ON agendamento_execucoes (agendamento_id, data_execucao);
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- V7 — E.D.I.T.H.: ownership, correlacao e replay protection
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS edith_conversation_link (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    edith_conversation_id VARCHAR(128) NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_edith_conversation_link_edith_id UNIQUE (edith_conversation_id)
);

CREATE INDEX IF NOT EXISTS ix_edith_conversation_link_usuario
    ON edith_conversation_link (usuario_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS edith_task_link (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    context_ref           VARCHAR(64)  NOT NULL,
    edith_conversation_id VARCHAR(128) NOT NULL,
    edith_message_id      VARCHAR(128),
    edith_task_id         VARCHAR(128) NOT NULL,
    edith_request_id      VARCHAR(128),
    client_request_id     VARCHAR(128) NOT NULL,
    source_action         VARCHAR(64)  NOT NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'QUEUED',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_edith_task_link_client_request UNIQUE (usuario_id, client_request_id),
    CONSTRAINT ux_edith_task_link_edith_task UNIQUE (edith_task_id)
);

CREATE INDEX IF NOT EXISTS ix_edith_task_link_usuario_conv
    ON edith_task_link (usuario_id, edith_conversation_id);

CREATE INDEX IF NOT EXISTS ix_edith_task_link_context_ref
    ON edith_task_link (context_ref);

CREATE TABLE IF NOT EXISTS edith_callback_nonce (
    id         BIGSERIAL PRIMARY KEY,
    nonce      VARCHAR(128) NOT NULL,
    request_id VARCHAR(128),
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_edith_callback_nonce UNIQUE (nonce)
);

CREATE INDEX IF NOT EXISTS ix_edith_callback_nonce_expires
    ON edith_callback_nonce (expires_at);

-- ---------------------------------------------------------------------------
-- V8 — captura automatica de gastos (MacroDroid / Atalhos iOS)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mobile_capture_devices (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    platform        VARCHAR(32)  NOT NULL,
    token_hash      VARCHAR(128) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at    TIMESTAMP,
    revoked_at      TIMESTAMP,
    last_test_ok_at TIMESTAMP,
    CONSTRAINT ux_mobile_capture_devices_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS ix_mobile_capture_devices_usuario
    ON mobile_capture_devices (usuario_id, active, created_at DESC);

CREATE TABLE IF NOT EXISTS mobile_capture_events (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           BIGINT       NOT NULL REFERENCES mobile_capture_devices(id) ON DELETE CASCADE,
    usuario_id          BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    source              VARCHAR(40)  NOT NULL,
    status              VARCHAR(40)  NOT NULL,
    client_event_id     VARCHAR(128),
    external_event_id   VARCHAR(128),
    fingerprint         VARCHAR(128),
    parser_name         VARCHAR(80),
    amount              NUMERIC(19, 2),
    currency            VARCHAR(8),
    merchant_raw        VARCHAR(300),
    merchant_normalized VARCHAR(200),
    package_name        VARCHAR(200),
    notification_title  VARCHAR(300),
    notification_text   VARCHAR(1000),
    card_hint           VARCHAR(40),
    confidence          NUMERIC(5, 4),
    transacao_id        BIGINT REFERENCES transacoes(id) ON DELETE SET NULL,
    error_message       VARCHAR(500),
    received_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    CONSTRAINT ux_mobile_capture_events_device_client
        UNIQUE (device_id, client_event_id)
);

CREATE INDEX IF NOT EXISTS ix_mobile_capture_events_usuario_status
    ON mobile_capture_events (usuario_id, status, received_at DESC);

CREATE INDEX IF NOT EXISTS ix_mobile_capture_events_fingerprint
    ON mobile_capture_events (usuario_id, fingerprint);

CREATE TABLE IF NOT EXISTS mobile_source_mappings (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    device_id    BIGINT       REFERENCES mobile_capture_devices(id) ON DELETE CASCADE,
    package_name VARCHAR(200),
    provider_key VARCHAR(80),
    card_last4   VARCHAR(8),
    conta_id     BIGINT       REFERENCES contas_bancarias(id) ON DELETE SET NULL,
    cartao_id    BIGINT       REFERENCES cartoes_credito(id) ON DELETE SET NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_mobile_source_mappings_lookup
    ON mobile_source_mappings (usuario_id, package_name, provider_key, enabled);

CREATE TABLE IF NOT EXISTS merchant_category_rules (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    merchant_pattern    VARCHAR(200)  NOT NULL,
    merchant_normalized VARCHAR(200),
    categoria_id        BIGINT        NOT NULL REFERENCES categorias(id) ON DELETE CASCADE,
    confidence          NUMERIC(5, 4) NOT NULL DEFAULT 1.0000,
    origin              VARCHAR(40)   NOT NULL DEFAULT 'USER',
    last_used_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_merchant_category_rules_user_pattern
        UNIQUE (usuario_id, merchant_pattern)
);

CREATE INDEX IF NOT EXISTS ix_merchant_category_rules_normalized
    ON merchant_category_rules (usuario_id, merchant_normalized);

-- Colunas de rastreio da ingestao na transacao: ausentes, quebram todo SELECT da entidade Transacao.
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS origem_transacao VARCHAR(40);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS external_event_id VARCHAR(128);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS external_provider VARCHAR(80);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS merchant_raw VARCHAR(300);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS merchant_normalized VARCHAR(200);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS ingestion_fingerprint VARCHAR(128);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMP;
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS ingestion_confidence NUMERIC(5, 4);
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS mobile_capture_event_id BIGINT;

CREATE INDEX IF NOT EXISTS ix_transacoes_ingestion_fingerprint
    ON transacoes (usuario_id, ingestion_fingerprint)
    WHERE ingestion_fingerprint IS NOT NULL;

-- ---------------------------------------------------------------------------
-- V3 — uma unica fatura nao quitada por cartao + competencia
-- Duplicatas historicas impedem o indice; nesse caso registra aviso e segue.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    CREATE UNIQUE INDEX IF NOT EXISTS ux_faturas_cartao_competencia_nao_quitada
        ON faturas (cartao_credito_id, date_trunc('month', data_vencimento))
        WHERE status NOT IN ('PAGA', 'CANCELADA')
          AND cartao_credito_id IS NOT NULL
          AND data_vencimento IS NOT NULL;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'ux_faturas_cartao_competencia_nao_quitada nao criado (duplicatas em aberto?): %', SQLERRM;
END $$;
