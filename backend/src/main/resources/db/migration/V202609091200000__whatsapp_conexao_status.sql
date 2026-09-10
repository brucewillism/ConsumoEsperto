-- Snapshot + histórico de sessão WhatsApp (Evolution). Sem credenciais.

CREATE TABLE IF NOT EXISTS whatsapp_conexao_status (
    usuario_id BIGINT PRIMARY KEY,
    instance_name VARCHAR(200),
    estado VARCHAR(40) NOT NULL,
    detalhe VARCHAR(500),
    estado_desde TIMESTAMP NOT NULL,
    verificado_em TIMESTAMP NOT NULL,
    tentativas_reconexao INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMP,
    ultima_tentativa_em TIMESTAMP,
    ultimo_resultado_reconexao VARCHAR(40),
    alerta_desconexao_enviado BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS whatsapp_conexao_transicao (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    instance_name VARCHAR(200),
    estado_anterior VARCHAR(40),
    estado_novo VARCHAR(40) NOT NULL,
    detalhe VARCHAR(500),
    origem VARCHAR(40) NOT NULL,
    verificado_em TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wa_conexao_transicao_user
    ON whatsapp_conexao_transicao (usuario_id, verificado_em DESC);
