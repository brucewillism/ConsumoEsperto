# Auditoria E.D.I.T.H. — ConsumoEsperto

Inventário do código real **antes** do complemento desta iteração.  
Legenda: **EXISTE** | **PARCIAL** | **NÃO EXISTE**.

O estado **depois** do complemento está em `docs/CONSUMO_EDITH_INTEGRATION.md` e `docs/CONSUMO_INTEGRATION_REPORT.md`.

ConsumoEsperto permanece dono do domínio financeiro. E.D.I.T.H. não acessa PostgreSQL. A aplicação deve funcionar com `EDITH_ENABLED=false`.

---

## Componentes pedidos

| Item | Status | Onde / nota |
|------|--------|-------------|
| `EdithHttpClient` | **EXISTE** | `edith/client/EdithHttpClient.java` — RestTemplate + Java HttpClient SSE. Timeout único (`requestTimeoutMs`). Sem circuit breaker nem retry próprios. |
| `EdithProperties` | **PARCIAL** | `config/EdithProperties.java` + `application.properties`. Há `enabled`, `baseUrl`, `apiKey`, `callbackSecret`, timeouts de request/task/SSE. Faltam connect/read separados, retry, circuit breaker, fallback legado. |
| `CognitiveGatewaySelector` | **PARCIAL** | Escolhe Edith **ou** legacy pela flag. **Não** tenta Edith e cai no legado se a hub cair. Sem capability local. |
| `EdithCognitiveGateway` | **EXISTE** | Cria/reusa conversa e envia mensagem. `awaitCompletion` faz polling. |
| `LegacyCognitiveGateway` | **EXISTE** | `WhatsAppCommandService.processJarvisCommand`. Ativo só com flag off (via selector). |
| `/api/edith/*` | **EXISTE** | `EdithController`: status, conversations, messages, task, SSE. JWT. |
| SSE | **PARCIAL** | `EdithSseService` relê eventos da hub. Upstream aceita `Last-Event-ID`; o subscribe interno passa `null`. Frontend não reenvia `Last-Event-ID`. Eventos são status + resultado final, não tokens. |
| `edith_conversation_link` | **EXISTE** | Entidade + Flyway `V7__edith_integration.sql` + repositório. |
| `edith_task_link` | **EXISTE** | Idem, com `context_ref`, `client_request_id`, ownership. |
| `edith_callback_nonce` | **EXISTE** | Anti-replay + cleanup agendado. |
| Tool Bridge | **EXISTE** | `POST /api/internal/edith/tools` (permitAll + HMAC). |
| HMAC | **EXISTE** | `EdithHmacSigner` + `EdithCallbackSecurityService`. Testes de contrato. |
| Nonce | **EXISTE** | Unique no banco; replay → 409. Teste unitário. |
| Rate limit | **PARCIAL** | Contador in-memory por IP no controller (não distribuído). |
| Allowlist | **PARCIAL** | 3 tools read-only. Write (`finance.transaction.create`) negada. Faltam cards/month/cashflow/subscriptions/recurring/category. |
| Ownership | **EXISTE** | Task/conversa por `usuarioId`; tools resolvem user via `context_ref`; fatura via `FaturaService.buscarPorId(id, usuarioId)`. |
| `context_ref` | **EXISTE** | Obrigatório no callback; opaco; gerado no envio. |
| WhatsApp cognitive routing | **PARCIAL** | Fast path local **antes** de Edith. Edith só se enabled+operational. Falha Edith cai no LLM legado (não é o mesmo “fallback configurável” do selector). Comandos determinísticos não exigem Edith. |
| `WebAiChatController` | **PARCIAL** | `/api/ia-chat`. Se Edith on e não operacional → **503** (quebra o chat). Sem capability local, sem application context, `awaitCompletion=true` (sem SSE no JARVIS). |
| `EdithAssistantComponent` | **EXISTE** | Chat separado no dashboard. Só funciona se status `AVAILABLE`. Não é o painel J.A.R.V.I.S. |
| `EdithService` (Angular) | **EXISTE** | HTTP + SSE via `fetch`. Sem `Last-Event-ID`, sem application context. |

---

## Fluxo cognitivo desejado vs atual

| Etapa | Status |
|-------|--------|
| Capability financeira local/determinística primeiro | **NÃO EXISTE** (botões mandam frase ao `/api/ia-chat`) |
| `EDITH_ENABLED`? | **EXISTE** |
| E.D.I.T.H. disponível? | **PARCIAL** (`isConfigured()`, sem circuit OPEN) |
| Fallback legado se hub cair | **NÃO EXISTE** no selector; WhatsApp tem degradação parcial |
| Degradação controlada sem 5xx no chat | **NÃO EXISTE** (`WebAiChatController` devolve 503) |
| Erro Edith não derruba HTTP quando há fallback | **NÃO EXISTE** no chat web |

---

## Configuração

| Variável | Status |
|----------|--------|
| `EDITH_ENABLED` | **EXISTE** (default `false`) |
| `EDITH_BASE_URL` | **EXISTE** |
| `EDITH_API_KEY` | **EXISTE** |
| `EDITH_CALLBACK_SECRET` | **EXISTE** |
| `EDITH_CONNECT_TIMEOUT` | **NÃO EXISTE** (só `EDITH_REQUEST_TIMEOUT_MS` para connect **e** read) |
| `EDITH_READ_TIMEOUT` | **NÃO EXISTE** |
| `EDITH_RETRY` | **NÃO EXISTE** |
| `EDITH_MAX_RETRIES` | **NÃO EXISTE** |
| `EDITH_CIRCUIT_BREAKER` | **NÃO EXISTE** (há Resilience4j `default` no Actuator, **não** usado no cliente Edith) |
| `EDITH_FALLBACK_ENABLED` / `LEGACY_AI_FALLBACK_ENABLED` | **NÃO EXISTE** |

Nada de URL/chave hardcoded no Java; valores vêm de properties/env.

---

## Circuit breaker

| Item | Status |
|------|--------|
| Biblioteca Resilience4j no `pom` | **EXISTE** (`resilience4j-spring-boot2`) |
| Instância `edith` OPEN → fallback imediato | **NÃO EXISTE** |
| HALF_OPEN para recuperação | **NÃO EXISTE** no cliente Edith |
| Health da app DOWN por Edith | **EXISTE** proteção: `EdithHealthIndicator` sempre `UP` com detalhe DISABLED/AVAILABLE/UNAVAILABLE |

---

## AI Token Suppressor (ATS)

| Item | Status |
|------|--------|
| Dependência obrigatória do ConsumoEsperto | **NÃO EXISTE** (correto) |
| ATS no caminho E.D.I.T.H. | **NÃO EXISTE** (correto) |
| ATS opcional no IA legado | **EXISTE** — `TokenSuppressorService.tryOptimize` via `AiGatewayService`; falha devolve prompt original |

Não criar ATS como requisito desta integração.

---

## J.A.R.V.I.S. / Application Context / SSE

| Item | Status |
|------|--------|
| Browser → `127.0.0.1:4242` | **NÃO EXISTE** (correto) |
| Angular JARVIS → backend Consumo | **EXISTE** (`IaChatService` → `/api/ia-chat`) |
| JARVIS = mesmo assistente + Edith opcional | **PARCIAL** — dois UIs (JARVIS + `app-edith-assistant`) |
| `application_id=consumo-esperto` no payload Edith | **NÃO EXISTE** (envia `project` / título `CONSUMO_ESPERTO`) |
| `screen`, `entity_type`, `entity_id` | **NÃO EXISTE** |
| Validação ownership de `entity_id` no backend | **NÃO EXISTE** (campo ainda não é enviado) |
| `trace_id` / MDC | **PARCIAL** — métricas JARVIS próprias; não propaga trace Edith |
| Streaming progressivo no chat JARVIS | **NÃO EXISTE** (POST síncrono espera texto inteiro) |
| Last-Event-ID reconexão | **PARCIAL** (só no cliente HTTP da hub) |

---

## Capabilities / Tool Bridge

| Tool | Status |
|------|--------|
| `finance.accounts.list` | **EXISTE** (service existente, payload reduzido) |
| `finance.transactions.search` | **EXISTE** |
| `finance.invoice.read` | **PARCIAL** — ownership ok; `principais_itens` pode vazar DTO de transação |
| `finance.cards.list` | **NÃO EXISTE** |
| `finance.month.summary` | **NÃO EXISTE** (service `TransacaoService.resumoFinanceiroMes` **existe**) |
| `finance.cashflow.project` | **NÃO EXISTE** (service `MotorFinanceiroService.calcular(..., false)` **existe**) |
| `finance.subscriptions.list` | **NÃO EXISTE** (`AssinaturaRecorrenteService.listar`) |
| `finance.recurring.list` | **NÃO EXISTE** (`AgendamentoPagamentoService.listar`) |
| `finance.category.summary` | **NÃO EXISTE** |
| `finance:write` bloqueado | **EXISTE** |

---

## Botões rápidos JARVIS

| Botão | Status |
|-------|--------|
| Guia de Uso (`tutorial`) | **PARCIAL** — vai ao `/api/ia-chat` (legado local se Edith off; Edith se on) |
| Listar meus cartões | **NÃO EXISTE** mapeamento `finance.cards.list` — envia frase ao LLM/Edith |
| Como vou fechar o mês? | **NÃO EXISTE** mapeamento month/cashflow |

---

## Health / startup / segurança

| Item | Status |
|------|--------|
| Edith offline não impede boot | **EXISTE** (flag false; cliente lazy; sem `@PostConstruct` obrigatório) |
| Health core ≠ Edith | **PARCIAL** — indicator `edith` não derruba UP; `/actuator/health` público sem componentes; **não** há `core/database/whatsapp/assistant` para o front |
| JWT | **EXISTE** em `/api/edith/*` e `/api/ia-chat` |
| Tool Bridge HMAC fail-closed | **EXISTE** |
| Fail-open em autorização | **NÃO EXISTE** (correto) |

---

## Testes pedidos

| Caso | Status |
|------|--------|
| `EDITH_ENABLED=false` status/startup | **EXISTE** (`EdithFeatureFlagHttpTest`) |
| Edith offline, listar cartões (CRUD) | **NÃO EXISTE** (CRUD não depende de Edith; falta prova explícita) |
| Edith offline, CRUD financeiro | **PARCIAL** — suíte geral com flag off; sem teste nomeado |
| Edith offline, cognitivo → legacy/degradação | **NÃO EXISTE** |
| Circuit fecha na recuperação | **NÃO EXISTE** |
| HMAC inválido 401 | **EXISTE** |
| Nonce replay bloqueado | **EXISTE** (unitário; HTTP replay **NÃO EXISTE**) |
| `finance:write` bloqueado | **EXISTE** |
| Botão listar cartões 0 LLM | **NÃO EXISTE** |
| SSE reconexão | **NÃO EXISTE** |
| Ownership `entity_id` | **PARCIAL** — task alheia 404 (`EdithOwnershipHttpTest`); entity de fatura no chat **NÃO EXISTE** |

---

## Frontend estados do assistente

| Estado | Status |
|--------|--------|
| Online / Disponível | **PARCIAL** (`EdithAssistantComponent`: Disponível/Indisponível/Desabilitado) |
| Modo local | **NÃO EXISTE** no JARVIS |
| Modo degradado | **NÃO EXISTE** |
| E.D.I.T.H. indisponível | **PARCIAL** (assistente Edith; JARVIS só “núcleo não respondeu”) |

---

## Documentação

| Item | Status na auditoria |
|------|---------------------|
| `docs/EDITH_INTEGRACAO.md` | **EXISTE** (não recriar; complementar) |
| `CONSUMO_EDITH_AUDIT.md` | este arquivo |
| `CONSUMO_EDITH_INTEGRATION.md` | a produzir após o complemento |
| `CONSUMO_INTEGRATION_REPORT.md` | a produzir após o complemento |

---

## Conclusão da auditoria

A espinha (cliente HTTP, flag, tool bridge HMAC, links, SSE básico, gateways, WhatsApp fast path) **já existe**.  
O que falta para o contrato desta iteração: **fallback legado configurável**, **circuit breaker no cliente Edith**, **capabilities locais nos botões**, **application context + trace**, **tools read-only adicionais**, **health separado para o assistente**, **JARVIS com modo/SSE**, **testes dos caminhos de degradação**.
