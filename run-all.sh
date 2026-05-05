#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# Run-all local: Backend (Spring) + Evolution (Node)
# =========================================================
# Uso:
#   bash run-all.sh
#
# Requisitos:
# - Java 17 disponível no terminal
# - Node.js/npm instalados
# - Evolution já preparada via setup-evolution-native.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
EVOLUTION_DIR="${ROOT_DIR}/../evolution-api"
LOG_DIR="${ROOT_DIR}/logs"

SPRING_PORT="${SPRING_PORT:-8081}"
EVOLUTION_PORT="${EVOLUTION_PORT:-8080}"
SPRING_PROFILE="${SPRING_PROFILE:-dev-evolution}"

mkdir -p "${LOG_DIR}"

if [[ ! -d "${BACKEND_DIR}" ]]; then
  echo "ERRO: backend não encontrado em ${BACKEND_DIR}"
  exit 1
fi

if [[ ! -d "${EVOLUTION_DIR}" ]]; then
  echo "ERRO: Evolution não encontrada em ${EVOLUTION_DIR}"
  echo "Rode primeiro: bash setup-evolution-native.sh"
  exit 1
fi

echo "==> Subindo backend Spring Boot na porta ${SPRING_PORT} (perfil ${SPRING_PROFILE})..."
(
  cd "${BACKEND_DIR}"
  nohup ./mvnw spring-boot:run \
    -Dspring-boot.run.arguments="--server.port=${SPRING_PORT} --spring.profiles.active=${SPRING_PROFILE}" \
    > "${LOG_DIR}/backend.log" 2>&1 &
  echo $! > "${LOG_DIR}/backend.pid"
)

echo "==> Subindo Evolution API na porta ${EVOLUTION_PORT}..."
(
  cd "${EVOLUTION_DIR}"
  nohup npm run dev > "${LOG_DIR}/evolution.log" 2>&1 &
  echo $! > "${LOG_DIR}/evolution.pid"
)

echo ""
echo "Processos iniciados em background."
echo "- Backend log:   ${LOG_DIR}/backend.log"
echo "- Evolution log: ${LOG_DIR}/evolution.log"
echo ""
echo "PIDs:"
echo "- Backend:   $(cat "${LOG_DIR}/backend.pid")"
echo "- Evolution: $(cat "${LOG_DIR}/evolution.pid")"
echo ""
echo "Health checks:"
echo "- Backend:   http://localhost:${SPRING_PORT}/actuator/health (ou /api/public/whatsapp/health)"
echo "- Evolution: http://localhost:${EVOLUTION_PORT}/health"
echo ""
echo "Para parar:"
echo "  kill \$(cat \"${LOG_DIR}/backend.pid\") \$(cat \"${LOG_DIR}/evolution.pid\")"
