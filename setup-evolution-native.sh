#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# Setup Evolution API v2 em Node.js nativo
# =========================================================

EVOLUTION_REPO_URL="${EVOLUTION_REPO_URL:-https://github.com/EvolutionAPI/evolution-api.git}"
EVOLUTION_DIR="${EVOLUTION_DIR:-../evolution-api}"
PROJECT_API_KEY="${PROJECT_API_KEY:-42abc123}"
WEBHOOK_URL="${WEBHOOK_URL:-http://localhost:8081/api/public/evolution/webhook}"
EVOLUTION_PORT="${EVOLUTION_PORT:-8080}"

echo "==> Projeto atual: $(pwd)"
echo "==> Repositório Evolution: ${EVOLUTION_REPO_URL}"
echo "==> Pasta Evolution: ${EVOLUTION_DIR}"

if ! command -v git >/dev/null 2>&1; then
  echo "ERRO: git não encontrado no PATH."
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "ERRO: node não encontrado no PATH."
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "ERRO: npm não encontrado no PATH."
  exit 1
fi

if [[ ! -d "${EVOLUTION_DIR}" ]]; then
  echo "==> Clonando Evolution API..."
  git clone "${EVOLUTION_REPO_URL}" "${EVOLUTION_DIR}"
else
  echo "==> Pasta Evolution já existe. Pulando clone."
fi

echo "==> Instalando dependências..."
(
  cd "${EVOLUTION_DIR}"
  npm install
)

echo "==> Gerando .env local para testes..."
cat > "${EVOLUTION_DIR}/.env" <<EOF
# -----------------------------------------
# Evolution API local em Node.js
# -----------------------------------------
SERVER_PORT=${EVOLUTION_PORT}

AUTHENTICATION_API_KEY=${PROJECT_API_KEY}
AUTHENTICATION_TYPE=apikey

# Armazenamento de mensagens em memória/cache para ciclo rápido
STORE_MESSAGES=true
DATABASE_ENABLED=false

# Alguns builds usam cache redis; manter desativado neste modo
CACHE_REDIS_ENABLED=false

# Webhook global para o backend Spring local
WEBHOOK_GLOBAL_ENABLED=true
WEBHOOK_GLOBAL_URL=${WEBHOOK_URL}

# Recomendado para payloads de mídia maiores (base64)
WEBHOOK_REQUEST_TIMEOUT_MS=120000
EOF

echo "==> Setup concluído."
echo ""
echo "Próximos passos:"
echo "1) cd \"${EVOLUTION_DIR}\""
echo "2) npm run dev   # desenvolvimento"
echo "   ou"
echo "   npm run start # produção"
echo "3) verificar health: http://localhost:${EVOLUTION_PORT}/health"
echo ""
echo "Observação:"
echo "- Com DATABASE_ENABLED=false você testa webhook/áudio/imagem sem Postgres."
echo "- Sem persistência, reiniciar a Evolution pode perder sessões/estado."
