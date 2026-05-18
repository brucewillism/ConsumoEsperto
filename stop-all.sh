#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${ROOT_DIR}/logs"

BACKEND_PID_FILE="${LOG_DIR}/backend.pid"
EVOLUTION_PID_FILE="${LOG_DIR}/evolution.pid"

kill_from_pid_file() {
  local pid_file="$1"
  local name="$2"

  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(cat "${pid_file}" || true)"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      echo "Parando ${name} (PID ${pid})..."
      kill "${pid}" >/dev/null 2>&1 || true
      sleep 1
      if kill -0 "${pid}" >/dev/null 2>&1; then
        echo "${name} ainda ativo; forçando kill -9..."
        kill -9 "${pid}" >/dev/null 2>&1 || true
      fi
    else
      echo "PID de ${name} não está ativo (${pid:-vazio})."
    fi
    rm -f "${pid_file}"
  else
    echo "Arquivo PID não encontrado para ${name}: ${pid_file}"
  fi
}

echo "==> Encerrando processos por PID salvo..."
kill_from_pid_file "${BACKEND_PID_FILE}" "backend"
kill_from_pid_file "${EVOLUTION_PID_FILE}" "evolution"

echo "==> Fallback de portas da stack (18080, 18081, 14200, 14040)..."
if command -v lsof >/dev/null 2>&1; then
  pids="$(lsof -ti:18080,18081,14200,14040 || true)"
  if [[ -n "${pids}" ]]; then
    echo "${pids}" | xargs kill -9 >/dev/null 2>&1 || true
    echo "Processos nessas portas finalizados com kill -9."
  else
    echo "Nenhum processo escutando nessas portas."
  fi
else
  echo "lsof não encontrado; fallback de portas ignorado."
fi

echo "Concluído."
