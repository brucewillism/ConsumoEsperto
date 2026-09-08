# Integração E.D.I.T.H. no ConsumoEsperto

ConsumoEsperto continua sendo o **dono do domínio financeiro**.  
E.D.I.T.H. é um gateway cognitivo **opcional**. A aplicação financeira funciona com `EDITH_ENABLED=false` ou com a hub offline.

A IA deve melhorar o ConsumoEsperto. Ela **nunca** é requisito para login, CRUD, faturas, importações, relatórios ou cálculos determinísticos.

## Papéis

| Direção | Fluxo |
|---------|--------|
| Cliente | Angular J.A.R.V.I.S. → Backend Consumo → capability local **ou** E.D.I.T.H. |
| Tool provider | E.D.I.T.H. → `POST /api/internal/edith/tools` (HMAC) → Service existente → PostgreSQL |

O navegador **não** chama JARVIS local (`127.0.0.1:4242`) nem a API E.D.I.T.H.  
E.D.I.T.H. **não** acessa o PostgreSQL.

ATS (AI Token Suppressor) permanece só no pipeline de IA legado. ConsumoEsperto não depende do ATS para falar com E.D.I.T.H.

## Gateway cognitivo

```
request
  → capability financeira local/determinística?  → SIM → LocalFinanceCapabilityService (0 LLM)
  → EDITH_ENABLED e hub operacional (circuito não OPEN)?
       → SIM → EdithCognitiveGateway
            ↳ falha → LEGACY_AI_FALLBACK_ENABLED? → LegacyCognitiveGateway
            ↳ senão → DEGRADED (HTTP 200, sem 5xx)
  → flag off → LegacyCognitiveGateway (modo LOCAL)
```

Erro da hub **não** derruba a requisição HTTP do chat quando há fallback ou degradação controlada.

WhatsApp **não** usa `dispatch()` no cognitivo: `EdithJarvisRoutingService.tryCognitiveReply` fala com a hub e, se falhar, devolve vazio para o parser/LLM legado. Isso evita recursão (`LegacyCognitiveGateway` → `WhatsAppCommandService` → Edith). Comandos determinísticos (fast path, lista de cartões, tutorial) continuam locais **antes** da IA.

## Application Context

O backend envia para E.D.I.T.H. (quando aplicável):

- `application_id` = `consumo-esperto` (`EDITH_APPLICATION_ID`)
- `screen`
- `entity_type` / `entity_id` (só depois de ownership)
- `conversation_id`
- `trace_id`

`entity_id` de fatura só entra no contexto se `FaturaService.buscarPorId(id, usuarioId)` confirmar posse. IDOR não é confiável no payload do front.

O front manda contexto mínimo (dashboard / invoices), nunca o estado inteiro da página.

## Capabilities locais (0 LLM)

Usadas pelos botões do J.A.R.V.I.S. e por frases equivalentes. Chamam **services existentes**.

| Capability | Origem no chat | Service |
|------------|----------------|---------|
| `finance.cards.list` | “Listar meus cartões” | `CartaoCreditoService` |
| `finance.month.summary` / `finance.cashflow.project` | “Como vou fechar o mês?” | `TransacaoService` + `MotorFinanceiroService` |

Tutorial / ajuda / sair continuam no pipeline Jarvis local.

## Tool Bridge (read-only)

HMAC + timestamp + nonce anti-replay + rate limit in-memory + `context_ref` opaco + ownership via task link.

`finance.transaction.create` (write) continua **bloqueado** (`TOOL_BRIDGE_DENIED`).

Tools read-only (payload reduzido, sem PAN de cartão):

- `finance.accounts.list`
- `finance.transactions.search`
- `finance.invoice.read`
- `finance.cards.list`
- `finance.month.summary`
- `finance.cashflow.project`
- `finance.subscriptions.list`
- `finance.recurring.list`
- `finance.category.summary`

## Circuit breaker e retry

Resilience4j instância `edith` (`registerHealthIndicator=false` — não derruba o Actuator).

- Após falhas suficientes: **OPEN** → cliente falha imediato (`EDITH_UNAVAILABLE`), sem esperar timeout HTTP.
- Selector usa fallback legado ou degradação.
- **HALF_OPEN** automático para detectar recuperação; sucesso fecha o circuito.

Retry só em `ResourceAccessException` (rede), não em 4xx.

## SSE / streaming

- `GET /api/edith/tasks/{taskId}/events` (JWT)
- Header `Last-Event-ID` preservado até a hub
- Eventos com `id` sequencial; o front reconecta com o último id
- Deltas (`delta` / `token` / `chunk`) são reencaminhados para o chat aparecer progressivamente
- Chat J.A.R.V.I.S.: se `/api/ia-chat` devolve `202` + `taskId` (`mode=EDITH`), assina o SSE em vez de esperar o texto inteiro

## Health e startup

- `GET /api/runtime-health` (JWT): `core`, `database`, `edith`, `whatsapp`, `assistant`
- Actuator `edith`: sempre `UP` no health agregado, com detalhe DISABLED / AVAILABLE / UNAVAILABLE
- E.D.I.T.H. offline **não** impede o Spring Boot de subir e **não** marca a aplicação como DOWN

Estados do assistente (`assistant`): `ONLINE` | `LOCAL` | `DEGRADED` | `EDITH_UNAVAILABLE`

## Configuração

Nada hardcoded. Ver `.env.example` e `application.properties`.

| Variável | Padrão | Uso |
|----------|--------|-----|
| `EDITH_ENABLED` | `false` | Liga o cliente/hub |
| `EDITH_BASE_URL` | vazio | Base HTTP da hub |
| `EDITH_API_KEY` | vazio | Auth backend-only |
| `EDITH_CALLBACK_SECRET` | vazio | HMAC do Tool Bridge |
| `EDITH_APPLICATION_ID` | `consumo-esperto` | Context `application_id` |
| `EDITH_CONNECT_TIMEOUT` / `EDITH_CONNECT_TIMEOUT_MS` | `5000` | Connect |
| `EDITH_READ_TIMEOUT` / `EDITH_READ_TIMEOUT_MS` | `15000` | Read |
| `EDITH_RETRY` | `true` | Retry de transporte |
| `EDITH_MAX_RETRIES` | `2` | Tentativas extras |
| `EDITH_CIRCUIT_BREAKER` | `true` | Circuito `edith` |
| `LEGACY_AI_FALLBACK_ENABLED` / `EDITH_FALLBACK_ENABLED` | `true` | Fallback para IA legada |

Combinação típica de migração incremental:

```
EDITH_ENABLED=true
LEGACY_AI_FALLBACK_ENABLED=true
```

## Frontend J.A.R.V.I.S.

Identidade visual permanece J.A.R.V.I.S.  
Badge: Online / Modo local / Modo degradado / E.D.I.T.H. indisponível.  
Detalhe técnico (circuito, URLs) fica no assistente E.D.I.T.H. / admin.

## Segurança

Não foi afrouxado: JWT, ownership/IDOR, HMAC fail-closed, nonce, rate limit.  
Fail-open só em otimizações (ex.: health da hub). **Nunca** em autorização.
