# Relatório de integração E.D.I.T.H. — ConsumoEsperto

Sem commit e sem push. Outros repositórios não foram alterados.

## Estado anterior

A espinha já existia (auditoria em `docs/CONSUMO_EDITH_AUDIT.md`):

- `EdithHttpClient`, `EdithProperties`, `/api/edith/*`, SSE, tabelas `edith_*`
- Tool Bridge HMAC + nonce + allowlist read-only (3 tools)
- Gateways Edith e legado, selector **sem** fallback em falha da hub
- Chat web `/api/ia-chat` podia devolver **503** se a flag estivesse on e a hub down
- Botões do J.A.R.V.I.S. mandavam a frase ao LLM
- ATS só no IA legado (mantido assim)
- Resilience4j no pom, **não** no cliente Edith
- App já subia com `EDITH_ENABLED=false`

## O que foi completado

- Gateway: capability local → Edith se operacional → fallback legado configurável → degradação sem 5xx
- Circuit breaker + retry no cliente (`EdithResilience`, instância `edith`)
- Timeouts connect/read separados
- Application context + `trace_id` + ownership de `entity_id` de fatura
- Tools read-only extras (cards, month, cashflow, subscriptions, recurring, category)
- Health separado `GET /api/runtime-health`
- Chat J.A.R.V.I.S. com estados, capabilities nos botões e SSE + `Last-Event-ID`
- Variáveis novas em `.env.example` e `docker-compose.yml`

## Arquivos principais

### Backend (novos)

- `edith/EdithResilience.java`
- `edith/EdithTraceContext.java`
- `edith/LocalFinanceCapabilityService.java`
- `edith/ConsumoRuntimeHealthService.java`
- `controller/ConsumoRuntimeHealthController.java`
- `edith/tools/FinanceCardsListTool.java`
- `edith/tools/FinanceMonthSummaryTool.java`
- `edith/tools/FinanceCashflowProjectTool.java`
- `edith/tools/FinanceSubscriptionsListTool.java`
- `edith/tools/FinanceRecurringListTool.java`
- `edith/tools/FinanceCategorySummaryTool.java`

### Backend (alterados)

- `config/EdithProperties.java`, `application.properties`
- `edith/client/EdithHttpClient.java`
- `edith/CognitiveGatewaySelector.java`
- `edith/EdithCognitiveGateway.java` / `LegacyCognitiveGateway.java`
- `edith/EdithIntegrationService.java`
- `edith/EdithSseService.java`
- `edith/EdithToolBridgeService.java`
- `controller/WebAiChatController.java`, `EdithController.java`
- `edith/tools/EdithToolRegistry.java`, `FinanceInvoiceReadTool.java`

### Frontend

- `shared/jarvis-chat/*` (badge, capability, SSE)
- `services/ia-chat.service.ts`, `services/edith.service.ts`
- `shared/edith-assistant/edith-assistant.component.ts`
- `pages/dashboard/dashboard.component.html`

### Config / docs

- `.env.example`, `docker-compose.yml`
- `docs/CONSUMO_EDITH_AUDIT.md` (inventário pré-complemento)
- `docs/CONSUMO_EDITH_INTEGRATION.md`
- `docs/EDITH_INTEGRACAO.md` (complementado, não recriado)

## Capabilities

Locais (chat, 0 LLM): `finance.cards.list`, `finance.month.summary`, `finance.cashflow.project`.

Tool Bridge: as locais acima + `finance.accounts.list`, `finance.transactions.search`, `finance.invoice.read`, `finance.subscriptions.list`, `finance.recurring.list`, `finance.category.summary`.

Write (`finance.transaction.create`) bloqueado.

## Configs

`EDITH_ENABLED` (default false), `EDITH_BASE_URL`, `EDITH_API_KEY`, `EDITH_CALLBACK_SECRET`, `EDITH_APPLICATION_ID`, `EDITH_CONNECT_TIMEOUT`, `EDITH_READ_TIMEOUT`, `EDITH_RETRY`, `EDITH_MAX_RETRIES`, `EDITH_CIRCUIT_BREAKER`, `LEGACY_AI_FALLBACK_ENABLED` / `EDITH_FALLBACK_ENABLED`.

## Fallbacks

- Flag off → pipeline Jarvis legado (`LOCAL`)
- Hub down / circuito OPEN + fallback on → legado (`LEGACY` / assistente `DEGRADED`)
- Fallback off → texto de degradação (`DEGRADED` / `EDITH_UNAVAILABLE`), sem 5xx no chat
- WhatsApp: fast path local; cognitivo Edith; falha Edith → LLM legado (sem `dispatch` para não recursar)

## Circuit breaker

Resilience4j `edith`: janela 8, mínimo 4 chamadas, 50% falha, espera 10s, HALF_OPEN com 2 chamadas. Health indicator da instância **desligado**. OPEN → falha imediata no cliente.

## Fluxo J.A.R.V.I.S.

Angular (persona J.A.R.V.I.S.) → `/api/ia-chat` → capability local **ou** gateway.  
Se Edith aceitar a tarefa: `202` + `taskId` → SSE `/api/edith/tasks/{id}/events`.  
Nunca `Browser → 127.0.0.1:4242`.

## Application Context

Front: `application_id=consumo-esperto`, `screen` (ex. `dashboard`), opcionalmente `entity_type`/`entity_id`.  
Back: valida ownership antes de reenviar `entity_id`.

## SSE

Relay da hub, `Last-Event-ID`, ids sequenciais, deltas progressivos, reconexão limitada no front.

## Segurança

JWT nas APIs de usuário. Tool Bridge `permitAll` + HMAC fail-closed. Nonce replay → 409. Rate limit por IP. `context_ref` opaco.

## Testes

- `EDITH_ENABLED=false`: status, runtime-health, listar cartões, listar transações, botão cartões `mode=LOCAL`
- Selector: fallback legado, degradação, circuito OPEN
- Circuit: OPEN falha imediato; CLOSED recupera
- Tool Bridge: HMAC 401, nonce replay 409, write `TOOL_BRIDGE_DENIED`
- Ownership de `entity_id` de fatura
- SSE `Last-Event-ID` no cliente HTTP
- Front: capabilities dos botões (Karma)

## Pendências

- Rate limit do Tool Bridge continua in-memory (não distribuído)
- Tokens SSE dependem do contrato da hub (`delta`/`token`/`chunk`); se a hub só emitir status + resultado final, o chat mostra o texto ao completar
- WhatsApp cognitivo ainda não usa o mesmo `dispatch` do chat web (de propósito, para não recursar no legado)
- Remoção da IA legada: **não** nesta iteração
- ATS continua opcional e só no legado
- Verificação E2E visual no browser da sessão não foi feita (não há browser tools nesta sessão); cobertura por testes HTTP/unitários e `ng test` / `mvn`

## Regra final

Com `EDITH_ENABLED=false` ou hub offline: login, cadastro, dashboard, contas, transações, categorias, cartões, faturas, relatórios, importações PDF/CSV, agendamentos, despesas fixas, assinaturas, planejamento e cálculos determinísticos **continuam no ConsumoEsperto**.
