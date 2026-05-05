#!/usr/bin/env bash
set -euo pipefail

# ===============================
# ConsumoEsperto + Evolution API
# ===============================
# Edite as variaveis abaixo conforme seu ambiente.

EVOLUTION_URL="${EVOLUTION_URL:-http://localhost:8081}"
EVOLUTION_APIKEY="${EVOLUTION_APIKEY:-trocar_apikey_aqui}"
INSTANCE_NAME="${INSTANCE_NAME:-ConsumoEsperto}"
WEBHOOK_URL="${WEBHOOK_URL:-http://localhost:8081/api/public/evolution/webhook}"

echo "==> Evolution URL: ${EVOLUTION_URL}"
echo "==> Instance: ${INSTANCE_NAME}"
echo "==> Webhook: ${WEBHOOK_URL}"

headers=(-H "apikey: ${EVOLUTION_APIKEY}" -H "Content-Type: application/json")

call_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ -n "${body}" ]]; then
    curl -sS -X "${method}" "${url}" "${headers[@]}" -d "${body}"
  else
    curl -sS -X "${method}" "${url}" "${headers[@]}"
  fi
}

echo ""
echo "==> 1) Criando instancia..."
# Em algumas versoes o endpoint usa /instance/create e em outras /instance/create/{name}.
create_body="$(cat <<EOF
{
  "instanceName": "${INSTANCE_NAME}",
  "integration": "WHATSAPP-BAILEYS"
}
EOF
)"

if ! call_json POST "${EVOLUTION_URL}/instance/create" "${create_body}" >/tmp/evo_create.out 2>/tmp/evo_create.err; then
  echo "Falha em /instance/create, tentando /instance/create/${INSTANCE_NAME} ..."
  call_json POST "${EVOLUTION_URL}/instance/create/${INSTANCE_NAME}" "{}" >/tmp/evo_create.out 2>/tmp/evo_create.err || true
fi
cat /tmp/evo_create.out || true

echo ""
echo "==> 2) Configurando webhook (MESSAGES_UPSERT + base64)..."
webhook_body="$(cat <<EOF
{
  "enabled": true,
  "url": "${WEBHOOK_URL}",
  "webhook_by_events": false,
  "webhook_base64": true,
  "events": ["MESSAGES_UPSERT"]
}
EOF
)"

if ! call_json POST "${EVOLUTION_URL}/webhook/set/${INSTANCE_NAME}" "${webhook_body}" >/tmp/evo_webhook.out 2>/tmp/evo_webhook.err; then
  echo "Falha em /webhook/set, tentando /webhook/find/${INSTANCE_NAME} para diagnostico..."
  call_json GET "${EVOLUTION_URL}/webhook/find/${INSTANCE_NAME}" >/tmp/evo_webhook.out 2>/tmp/evo_webhook.err || true
fi
cat /tmp/evo_webhook.out || true

echo ""
echo "==> 3) Gerando QR Code / status de conexao..."
if ! call_json GET "${EVOLUTION_URL}/instance/connect/${INSTANCE_NAME}" >/tmp/evo_connect.out 2>/tmp/evo_connect.err; then
  echo "Falha em /instance/connect, tentando /instance/connectionState/${INSTANCE_NAME} ..."
  call_json GET "${EVOLUTION_URL}/instance/connectionState/${INSTANCE_NAME}" >/tmp/evo_connect.out 2>/tmp/evo_connect.err || true
fi
cat /tmp/evo_connect.out || true

echo ""
echo "==> 4) Validacao rapida da instancia..."
call_json GET "${EVOLUTION_URL}/instance/fetchInstances" >/tmp/evo_instances.out 2>/tmp/evo_instances.err || true
cat /tmp/evo_instances.out || true

echo ""
echo "Concluido."
echo "Agora: leia o QR no painel/retorno da Evolution e envie mensagem para testar."
