-- Garante no banco (PostgreSQL) que não existam duas faturas "em aberto" (não quitadas)
-- para o mesmo cartão na mesma competência (mês do vencimento).
-- Protege contra condição de corrida na criação de fatura (dois nós, retry, duplo clique).

-- 1) Saneamento: se houver duplicatas pré-existentes, mantém a fatura mais antiga (menor id)
--    por (cartão + competência), move as transações das duplicatas para ela e cancela as demais.
WITH grupos AS (
    SELECT cartao_credito_id,
           date_trunc('month', data_vencimento) AS competencia,
           min(id) AS keep_id
    FROM faturas
    WHERE status NOT IN ('PAGA', 'CANCELADA')
      AND cartao_credito_id IS NOT NULL
      AND data_vencimento IS NOT NULL
    GROUP BY cartao_credito_id, date_trunc('month', data_vencimento)
    HAVING count(*) > 1
),
duplicatas AS (
    SELECT f.id AS dup_id, g.keep_id
    FROM faturas f
    JOIN grupos g
      ON g.cartao_credito_id = f.cartao_credito_id
     AND date_trunc('month', f.data_vencimento) = g.competencia
    WHERE f.status NOT IN ('PAGA', 'CANCELADA')
      AND f.id <> g.keep_id
)
UPDATE transacoes t
SET fatura_id = d.keep_id
FROM duplicatas d
WHERE t.fatura_id = d.dup_id;

WITH grupos AS (
    SELECT cartao_credito_id,
           date_trunc('month', data_vencimento) AS competencia,
           min(id) AS keep_id
    FROM faturas
    WHERE status NOT IN ('PAGA', 'CANCELADA')
      AND cartao_credito_id IS NOT NULL
      AND data_vencimento IS NOT NULL
    GROUP BY cartao_credito_id, date_trunc('month', data_vencimento)
    HAVING count(*) > 1
)
UPDATE faturas f
SET status = 'CANCELADA'
FROM grupos g
WHERE f.cartao_credito_id = g.cartao_credito_id
  AND date_trunc('month', f.data_vencimento) = g.competencia
  AND f.status NOT IN ('PAGA', 'CANCELADA')
  AND f.id <> g.keep_id;

-- 2) Índice único parcial: uma única fatura não quitada por cartão + competência.
CREATE UNIQUE INDEX IF NOT EXISTS ux_faturas_cartao_competencia_nao_quitada
    ON faturas (cartao_credito_id, date_trunc('month', data_vencimento))
    WHERE status NOT IN ('PAGA', 'CANCELADA')
      AND cartao_credito_id IS NOT NULL
      AND data_vencimento IS NOT NULL;
