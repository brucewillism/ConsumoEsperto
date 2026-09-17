-- Outbox com estados e retry; categoria sugerida (MANUAL não aplica).

ALTER TABLE financial_domain_event
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';

ALTER TABLE financial_domain_event
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE financial_domain_event
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;

ALTER TABLE financial_domain_event
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

ALTER TABLE financial_domain_event
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64);

UPDATE financial_domain_event
SET processing_status = CASE WHEN processed THEN 'PROCESSED' ELSE 'PENDING' END
WHERE processing_status IS NULL OR processing_status = 'PENDING';

UPDATE financial_domain_event
SET processing_status = 'PROCESSED'
WHERE processed = TRUE AND processing_status <> 'PROCESSED';

CREATE INDEX IF NOT EXISTS ix_fin_domain_event_retry
    ON financial_domain_event (processing_status, next_retry_at, created_at);

ALTER TABLE transacoes
    ADD COLUMN IF NOT EXISTS categoria_sugerida_id BIGINT REFERENCES categorias(id) ON DELETE SET NULL;
