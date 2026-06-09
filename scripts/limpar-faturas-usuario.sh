#!/usr/bin/env bash
# Apaga todas as faturas de um usuário para reimportação limpa.
# Uso na VPS: ./scripts/limpar-faturas-usuario.sh [usuario_id]
set -euo pipefail

USUARIO_ID="${1:-1}"
cd "$(dirname "$0")/.."

echo "Limpando faturas do usuario_id=${USUARIO_ID}..."

docker compose exec -T postgres psql -U "${POSTGRES_USER:-consumo}" -d "${POSTGRES_DB:-consumo_db}" <<SQL
BEGIN;
DELETE FROM sugestoes_contencao_jarvis
WHERE usuario_id = ${USUARIO_ID}
  AND importacao_fatura_cartao_id IS NOT NULL;
DELETE FROM importacoes_fatura_cartao WHERE usuario_id = ${USUARIO_ID};
DELETE FROM transacoes WHERE usuario_id = ${USUARIO_ID} AND fatura_id IS NOT NULL;
DELETE FROM faturas WHERE cartao_credito_id IN (
  SELECT id FROM cartoes_credito WHERE usuario_id = ${USUARIO_ID}
);
COMMIT;
SELECT count(*) AS faturas_restantes FROM faturas
WHERE cartao_credito_id IN (SELECT id FROM cartoes_credito WHERE usuario_id = ${USUARIO_ID});
SQL

echo "Concluído."
