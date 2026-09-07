-- Extrato CSV no fluxo de importação de faturas (mesmo pending/confirm).

ALTER TABLE importacoes_fatura_cartao
    ADD COLUMN IF NOT EXISTS tipo_arquivo VARCHAR(40) NOT NULL DEFAULT 'INVOICE_PDF';

ALTER TABLE importacoes_fatura_cartao
    ADD COLUMN IF NOT EXISTS conta_bancaria_id BIGINT NULL;

ALTER TABLE importacoes_fatura_cartao
    ADD COLUMN IF NOT EXISTS arquivo_nome VARCHAR(255);

ALTER TABLE importacoes_fatura_cartao
    ADD COLUMN IF NOT EXISTS precisa_escolha_recurso BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_importacoes_fatura_conta_bancaria'
    ) THEN
        ALTER TABLE importacoes_fatura_cartao
            ADD CONSTRAINT fk_importacoes_fatura_conta_bancaria
            FOREIGN KEY (conta_bancaria_id) REFERENCES contas_bancarias (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_importacoes_fatura_tipo
    ON importacoes_fatura_cartao (usuario_id, tipo_arquivo, status);

-- Deduplicação cross-source (CSV × captura móvel × reimportação).
CREATE UNIQUE INDEX IF NOT EXISTS ux_transacoes_usuario_ingestion_fingerprint
    ON transacoes (usuario_id, ingestion_fingerprint)
    WHERE ingestion_fingerprint IS NOT NULL;
