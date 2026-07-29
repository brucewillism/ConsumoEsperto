-- Estruturas gerenciadas historicamente por SchemaAutoPatch (nao sao entidades JPA).
-- pgvector e opcional: embedding usa BYTEA quando a extensao nao esta instalada.

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector indisponivel — tabelas semanticas usarao BYTEA';
END $$;

CREATE TABLE IF NOT EXISTS public.evento_webhook_processado (
    id BIGSERIAL PRIMARY KEY,
    chave_dedup VARCHAR(512) NOT NULL,
    processado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_evento_webhook_chave
    ON public.evento_webhook_processado (chave_dedup);

CREATE TABLE IF NOT EXISTS public.memoria_semantica_jarvis (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES public.usuarios (id) ON DELETE CASCADE,
    contexto TEXT NOT NULL,
    embedding BYTEA,
    data_registro TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    categoria_origem VARCHAR(32) NOT NULL
        CHECK (categoria_origem IN ('FINANCAS', 'HABITO', 'AGENDA')),
    tipo VARCHAR(24) NOT NULL DEFAULT 'FATO',
    status VARCHAR(16) NOT NULL DEFAULT 'ATIVA',
    origem VARCHAR(24) NOT NULL DEFAULT 'SISTEMA',
    confianca NUMERIC(3, 2) NOT NULL DEFAULT 0.50,
    validade DATE,
    valor NUMERIC(19, 2),
    categoria VARCHAR(120),
    mes_alvo INTEGER,
    ano_alvo INTEGER,
    contador_reforco INTEGER NOT NULL DEFAULT 1,
    ultimo_reforco_em TIMESTAMP WITHOUT TIME ZONE,
    transacoes_evidencia TEXT,
    confirmada_usuario BOOLEAN,
    superada_por_id BIGINT,
    restaurada_em TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_mem_sem_jarvis_usuario_registro
    ON public.memoria_semantica_jarvis (usuario_id, data_registro DESC);

CREATE INDEX IF NOT EXISTS idx_mem_sem_jarvis_usuario_status
    ON public.memoria_semantica_jarvis (usuario_id, status, tipo);

CREATE TABLE IF NOT EXISTS public.transacao_semantica_index (
    id BIGSERIAL PRIMARY KEY,
    transacao_id BIGINT NOT NULL REFERENCES public.transacoes (id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL REFERENCES public.usuarios (id) ON DELETE CASCADE,
    texto_indexado TEXT NOT NULL,
    embedding BYTEA,
    atualizado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transacao_semantica_tx UNIQUE (transacao_id)
);

CREATE INDEX IF NOT EXISTS idx_transacao_semantica_usuario
    ON public.transacao_semantica_index (usuario_id);

CREATE TABLE IF NOT EXISTS public.jarvis_feedback (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES public.usuarios (id) ON DELETE CASCADE,
    insight_id VARCHAR(120) NOT NULL,
    tipo_alvo VARCHAR(32) NOT NULL,
    positivo BOOLEAN NOT NULL,
    categoria_chave VARCHAR(200),
    data_registro TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    data_expiracao TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_jarvis_fb_usuario_registro
    ON public.jarvis_feedback (usuario_id, data_registro DESC);

CREATE TABLE IF NOT EXISTS public.compra_parcelada_migracao_controle (
    compra_parcelada_id BIGINT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    grupo_parcela_id VARCHAR(64) NOT NULL,
    migrado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
