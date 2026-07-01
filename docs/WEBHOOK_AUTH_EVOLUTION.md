# Autenticação do webhook Evolution (ConsumoEsperto)

## Contexto crítico

A Evolution API v2 **não envia** o header `apikey` nos webhooks por padrão. Webhooks **globais** (`WEBHOOK_GLOBAL_URL` no `docker-compose.yml`) **não suportam headers customizados** — apenas webhooks **por instância** via API.

Se o backend exigir credencial e a Evolution não enviar, **todos os POSTs viram 401/503** e o WhatsApp para de responder **sem erro visível no telemóvel**.

## Mecanismo implementado (fail-closed)

| Modo | Como configurar | Quando usar |
|------|-----------------|-------------|
| **Token na query (recomendado no Compose)** | `WEBHOOK_GLOBAL_URL=.../webhook?token=SEU_SEGREDO` | Docker Compose com `WEBHOOK_GLOBAL_*` |
| **Header customizado** | `POST /webhook/set/{instance}` com `"headers": {"X-ConsumoEsperto-Webhook-Secret": "SEU_SEGREDO"}` | Produção com instância dedicada |
| **Header legado `apikey`** | Evolution REST envia `apikey` (raro em webhook outbound) | Compatibilidade |

### Variáveis de ambiente (VPS / `.env`)

```bash
EVOLUTION_WEBHOOK_SECRET=token_longo_e_aleatorio_distinto_da_api_key
# Opcional:
EVOLUTION_WEBHOOK_HEADER_NAME=X-ConsumoEsperto-Webhook-Secret
EVOLUTION_WEBHOOK_QUERY_PARAM=token
```

Spring (`application.properties`):

- `evolution.webhook.auth-required=true` (**default** — fail-closed)
- `evolution.webhook.secret=${EVOLUTION_WEBHOOK_SECRET:...}` (fallback: `evolution.apikey`)

### Docker Compose (já configurado)

```yaml
WEBHOOK_GLOBAL_URL=http://backend:8087/api/public/evolution/webhook?token=${EVOLUTION_WEBHOOK_SECRET:-${EVOLUTION_API_KEY:-changeme}}
```

Garanta que `EVOLUTION_WEBHOOK_SECRET` está definido no `.env` da VPS com valor forte.

## Configurar header customizado (instância dedicada)

Quando usar `EVOLUTION_DEDICATED_INSTANCE_PER_USER=true`, configure o webhook **por instância**:

```bash
curl -X POST "https://SUA-EVOLUTION/event/webhook/set/ce-u123" \
  -H "Content-Type: application/json" \
  -H "apikey: $EVOLUTION_API_KEY" \
  -d '{
    "webhook": {
      "enabled": true,
      "url": "https://seu-backend/api/public/evolution/webhook",
      "headers": {
        "X-ConsumoEsperto-Webhook-Secret": "'"$EVOLUTION_WEBHOOK_SECRET"'"
      },
      "events": ["MESSAGES_UPSERT", "CONNECTION_UPDATE"],
      "byEvents": false,
      "base64": false
    }
  }'
```

## Rollback de emergência

Se o WhatsApp ficar **mudo** após deploy:

1. No `.env` da VPS: `EVOLUTION_WEBHOOK_AUTH_REQUIRED=false` (ou `evolution.webhook.auth-required=false`)
2. Reinicie o backend: `docker compose up -d --build backend`
3. Confirme nos logs que aparece: `[WEBHOOK-AUTH] auth-required=false — rollback ativo`
4. Corrija a configuração da Evolution e reative `auth-required=true`

## Como testar (obrigatório — humano)

1. Com credencial correta: envie **mensagem real** no WhatsApp → log deve mostrar `Evolution webhook enfileirado (async)`
2. Sem credencial: `curl -X POST https://backend/api/public/evolution/webhook -d '{}'` → **401** + log `[WEBHOOK-AUTH] REJEITADO: credencial-ausente`
3. Token errado na query → **401** + `credencial-invalida`
4. Segredo não configurado com `auth-required=true` → **503** + `webhook-secret-not-configured`

## Número não vinculado

Mensagens de números **sem usuário** mapeado são ignoradas (`status=ignored`, `reason=unknown-user-or-instance`) quando `WHATSAPP_AUTO_PROVISION_USER=false`.

Com auto-provision ativo, números novos criam conta — desative em produção se quiser fail-closed por número.
