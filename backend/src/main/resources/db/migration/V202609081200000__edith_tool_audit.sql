-- Auditoria de invocação do Tool Bridge (E.D.I.T.H. → ConsumoEsperto).
-- Sem colunas de valor, descrição ou payload financeiro.

CREATE TABLE edith_tool_audit (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    tool_call_id VARCHAR(128),
    user_id BIGINT,
    capability VARCHAR(128) NOT NULL,
    params_json VARCHAR(2000) NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_edith_tool_audit_created ON edith_tool_audit (created_at);
CREATE INDEX idx_edith_tool_audit_user ON edith_tool_audit (user_id, created_at);
