-- E.D.I.T.H. — ownership local, correlação e replay protection (não duplica auditoria cognitiva)

CREATE TABLE IF NOT EXISTS edith_conversation_link (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    edith_conversation_id VARCHAR(128) NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    id          BIGSERIAL PRIMARY KEY,
    nonce       VARCHAR(128) NOT NULL,
    request_id  VARCHAR(128),
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_edith_callback_nonce UNIQUE (nonce)
);

CREATE INDEX IF NOT EXISTS ix_edith_callback_nonce_expires
    ON edith_callback_nonce (expires_at);
