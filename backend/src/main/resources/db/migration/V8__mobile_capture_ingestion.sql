-- Captura automática de gastos por dispositivos móveis (MacroDroid / Atalhos iOS)

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
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    device_id       BIGINT       REFERENCES mobile_capture_devices(id) ON DELETE CASCADE,
    package_name    VARCHAR(200),
    provider_key    VARCHAR(80),
    card_last4      VARCHAR(8),
    conta_id        BIGINT       REFERENCES contas_bancarias(id) ON DELETE SET NULL,
    cartao_id       BIGINT       REFERENCES cartoes_credito(id) ON DELETE SET NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_mobile_source_mappings_lookup
    ON mobile_source_mappings (usuario_id, package_name, provider_key, enabled);

CREATE TABLE IF NOT EXISTS merchant_category_rules (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    merchant_pattern    VARCHAR(200) NOT NULL,
    merchant_normalized VARCHAR(200),
    categoria_id        BIGINT       NOT NULL REFERENCES categorias(id) ON DELETE CASCADE,
    confidence          NUMERIC(5, 4) NOT NULL DEFAULT 1.0000,
    origin              VARCHAR(40)  NOT NULL DEFAULT 'USER',
    last_used_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_merchant_category_rules_user_pattern
        UNIQUE (usuario_id, merchant_pattern)
);

CREATE INDEX IF NOT EXISTS ix_merchant_category_rules_normalized
    ON merchant_category_rules (usuario_id, merchant_normalized);

-- Campos de rastreio na transação (origem da ingestão)
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
