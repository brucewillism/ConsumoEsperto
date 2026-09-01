# Integração E.D.I.T.H. — ConsumoEsperto

Gateway cognitivo central opcional. **Desligado por padrão** (`EDITH_ENABLED=false`).

## Arquitetura

```
Angular → Spring Boot (/api/edith/*) → E.D.I.T.H. API
E.D.I.T.H. → POST /api/internal/edith/tools (HMAC) → Finance Services → PostgreSQL
```

Nunca: `Angular → E.D.I.T.H.` direto.  
Nunca: `E.D.I.T.H. → JDBC → PostgreSQL`.

## Configuração

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `EDITH_ENABLED` | `false` | Feature flag |
| `EDITH_BASE_URL` | — | Base da API (ex. `http://edith_api:8080`) |
| `EDITH_API_KEY` | — | Chave backend-only |
| `EDITH_CALLBACK_SECRET` | — | HMAC do Tool Bridge |

Ver também `.env.example`.

## SDK

`io.edith:edith-java-sdk:0.4.1` **não disponível** no Nexus Maven configurado.  
Implementado adapter HTTP: `EdithHttpClient`.

## Endpoints (JWT)

- `GET /api/edith/status`
- `POST /api/edith/conversations`
- `POST /api/edith/conversations/{id}/messages` (+ `Idempotency-Key`)
- `GET /api/edith/tasks/{id}/events` (SSE)

## Tool Bridge (HMAC)

- `POST /api/internal/edith/tools`
- Allowlist read-only: `finance.accounts.list`, `finance.transactions.search`, `finance.invoice.read`

## Health

Actuator component `edith`: `DISABLED` | `AVAILABLE` | `UNAVAILABLE` (não derruba health core).

## Migração IA legada

| Uso atual | Migrado E.D.I.T.H.? | Pode remover? |
|-----------|----------------------|---------------|
| `WebAiChatController` | Sim (quando flag on) | Não |
| `WhatsAppCommandService` fast path | Não (local) | Não |
| `WhatsAppCommandService` cognitivo | Parcial (`EdithJarvisRoutingService`) | Não |
| OCR / faturas / contracheques | Não (fase 2) | Não |
| `AiRouterService` / providers | Não (fallback só com flag off) | Não |
