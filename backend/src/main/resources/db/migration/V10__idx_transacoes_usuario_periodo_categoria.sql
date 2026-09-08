-- Índice do filtro mais comum de finance.transactions.search: usuário + período + categoria.
-- data_transacao (não COALESCE com data_criacao) para o planejador usar o btree.
CREATE INDEX IF NOT EXISTS idx_transacoes_usuario_periodo_categoria
    ON transacoes (usuario_id, data_transacao DESC, categoria_id);
