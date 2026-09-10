-- Ingestão de notificações bancárias (MacroDroid / Atalhos iOS).
-- Token dedicado (hash only). Sem usuario_id no payload.

CREATE TABLE IF NOT EXISTS ingest_token (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash     VARCHAR(128) NOT NULL,
    prefixo        VARCHAR(16)  NOT NULL,
    criado_em      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_uso_em  TIMESTAMP,
    revogado       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ux_ingest_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS ix_ingest_token_usuario
    ON ingest_token (usuario_id, revogado, criado_em DESC);

CREATE TABLE IF NOT EXISTS ingest_preferencia (
    usuario_id          BIGINT PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    ativo               BOOLEAN     NOT NULL DEFAULT TRUE,
    agrupamento         VARCHAR(16) NOT NULL DEFAULT 'IMEDIATO',
    resumo_minutos      INTEGER     NOT NULL DEFAULT 15,
    silencioso_inicio   TIME,
    silencioso_fim      TIME,
    conta_padrao_id     BIGINT REFERENCES contas_bancarias(id) ON DELETE SET NULL,
    atualizado_em       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ingest_fonte_recurso (
    id                 BIGSERIAL PRIMARY KEY,
    usuario_id         BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    app                VARCHAR(32) NOT NULL,
    canal              VARCHAR(16) NOT NULL,
    conta_bancaria_id  BIGINT REFERENCES contas_bancarias(id) ON DELETE SET NULL,
    cartao_credito_id  BIGINT REFERENCES cartoes_credito(id) ON DELETE SET NULL,
    CONSTRAINT ux_ingest_fonte_usuario_app_canal UNIQUE (usuario_id, app, canal)
);

CREATE INDEX IF NOT EXISTS ix_ingest_fonte_usuario
    ON ingest_fonte_recurso (usuario_id);

CREATE TABLE IF NOT EXISTS notificacao_bancaria_recebida (
    id                       BIGSERIAL PRIMARY KEY,
    usuario_id               BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    origem                   VARCHAR(32)  NOT NULL,
    app                      VARCHAR(32)  NOT NULL,
    titulo                   VARCHAR(300),
    texto                    VARCHAR(1000) NOT NULL,
    recebido_em              TIMESTAMP    NOT NULL,
    id_externo               VARCHAR(128),
    hash_dedup               VARCHAR(64)  NOT NULL,
    status                   VARCHAR(24)  NOT NULL,
    transacao_id             BIGINT REFERENCES transacoes(id) ON DELETE SET NULL,
    transacao_enriquecida_id BIGINT REFERENCES transacoes(id) ON DELETE SET NULL,
    erro                     VARCHAR(500),
    aviso_pendente           BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_em            TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_notif_bancaria_id_externo
    ON notificacao_bancaria_recebida (usuario_id, id_externo)
    WHERE id_externo IS NOT NULL AND btrim(id_externo) <> '';

CREATE INDEX IF NOT EXISTS ix_notif_bancaria_usuario_criado
    ON notificacao_bancaria_recebida (usuario_id, criado_em DESC);

CREATE INDEX IF NOT EXISTS ix_notif_bancaria_hash
    ON notificacao_bancaria_recebida (usuario_id, hash_dedup, criado_em DESC);

CREATE INDEX IF NOT EXISTS ix_notif_bancaria_aviso
    ON notificacao_bancaria_recebida (aviso_pendente, usuario_id)
    WHERE aviso_pendente = TRUE;
