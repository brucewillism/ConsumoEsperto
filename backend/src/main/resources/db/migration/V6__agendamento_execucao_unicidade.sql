-- Idempotência de agendamentos no banco: exatamente um débito por
-- agendamento + competência (data de vencimento processada), mesmo com
-- dois nós, retry, restart ou execução manual simultânea ao scheduler.

CREATE TABLE IF NOT EXISTS agendamento_execucoes (
    id BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL REFERENCES agendamentos_pagamentos (id) ON DELETE CASCADE,
    data_execucao DATE NOT NULL,
    tipo_execucao VARCHAR(16) NOT NULL DEFAULT 'AUTOMATICA',
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

-- Conflito nesta chave = competência já processada (tratado pela aplicação,
-- nunca como erro 500).
CREATE UNIQUE INDEX IF NOT EXISTS ux_agendamento_execucoes_competencia
    ON agendamento_execucoes (agendamento_id, data_execucao);
