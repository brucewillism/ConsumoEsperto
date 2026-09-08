# Relatório — rodada 2 (ConsumoEsperto)

2026-09-07. Sem commit/push. Contrato `docs/ECOSYSTEM_CONTRACT.md` neste clone
ainda está rotulado **v1**; o patch v1.1 está em `00-CONTRATO-COMPARTILHADO.md`.
**Não alterei o contrato.** A implementação segue v1.1 (deadline duplo,
`envelope_synthesized`, `auth_scheme`, chat texto livre = `BALANCED`).

---

## Fase 0.1 — o que “sem token” significa

**É sem o segredo/LLM da E.D.I.T.H., com o JWT do usuário.** Não é um
endpoint financeiro anônimo.

Botões rápidos agora batem em `POST /api/capabilities/{id}:invoke` com a
sessão. A ponte `/api/ia-chat` exige o mesmo JWT. Sem `Authorization` a
resposta é **401**, corpo sem dado de ninguém.

### 1. Evidência `curl` (e Java HTTP) sem `Authorization`

Servidor de teste em porta aleatória. Pedido:

`POST /api/ia-chat` com `{"mensagem":"listar cartoes","capability":"finance.cards.list"}`,
**sem** header `Authorization`.

Java `HttpClient`: **401**. `curl.exe` (body em arquivo, para o quoting do
Windows não inventar URL):

```
HTTP/1.1 401
X-Eco-Trace-Id: tr_01M1YZG657G8F8JXE3DHJQBC3F
Content-Type: application/json;charset=UTF-8
```

Corpo (trecho):

```json
{"error":"Unauthorized","status":401,"path":"/api/ia-chat"}
```

Arquivos da suíte: `backend/target/fase01-curl-unauth.txt`,
`backend/target/fase01-curl-body.txt`.
Teste: `CapabilityAuthOwnershipHttpTest.curlSemAuthorizationRetorna401`.

`POST /api/capabilities/finance.cards.list:invoke` sem JWT também é **401**
(`invokeSemJwtE401`).

### 2. Ownership na Service, não só no controller

`CapabilityAuthOwnershipHttpTest.serviceLayerUsuarioANaoLeRecursosDeB`:

| Chamada | Código |
|---|---|
| `CartaoCreditoService.buscarPorId(cardB, userA)` | `SCOPE_DENIED` |
| `FaturaService.buscarPorId(fatB, userA)` | `SCOPE_DENIED` |
| `TransacaoService.buscarPorId(txB, userA)` | `SCOPE_DENIED` |

HTTP do invoke com JWT de A e `invoice_id` de B: **403** +
`error.code=SCOPE_DENIED`.

### 3. Todas as capabilities do `LocalFinanceCapabilityService`

Mesmo teste, `capabilitiesLocaisNaoVazamDadosDeB`. Usuário A invoca cada
capability local; o payload **não** contém cartão/fatura/transação de B.

| Capability | Resultado |
|---|---|
| `finance.cards.list` | lista vazia/própria; sem `CartaoSecretoB` |
| `finance.month.summary` | totais de A |
| `finance.accounts.list` | sem conta B |
| `finance.subscriptions.list` | sem vazamento |
| `finance.recurring.list` | sem vazamento |
| `finance.category.summary` | sem vazamento |
| `finance.cashflow.project` | sem vazamento |
| `finance.invoice.read` (`invoice_id` de B) | `SCOPE_DENIED` |
| `finance.transactions.search` (`account_id` de B) | `SCOPE_DENIED` |

`limit=101` em search → `INVALID_INPUT` (teto 100, sem truncar).

---

## Fase 0.2 — `finance.transactions.search`

Implementado:

1. **Pushdown.** Query nativa de IDs com `ORDER BY data_transacao DESC LIMIT n`,
   depois hydrate só esses IDs. Sem `Page`/`COUNT(*)`.
2. **Teto.** Default 50, máximo 100. Acima → `INVALID_INPUT`.
3. **Índice** `idx_transacoes_usuario_periodo_categoria`
   `(usuario_id, data_transacao DESC, categoria_id)`. Flyway `V10`.
4. **Curva 2.500 / 50.000 / 200.000.**
5. **DTO** `FinanceTransactionSearchItemDto` — campos deliberados (ver
   javadoc da classe e `docs/BASELINE_TOOLS.md` §4).

### Curva

| n | H2 p95 (ms) | Postgres p95 (ms) |
|---:|---:|---:|
| 2 500 | 43 | 20 |
| 50 000 | 61 | 9 |
| 200 000 | 146 | 12 |

**Aceite (“p95 estável”): cumprido no Postgres.** 20 → 9 → 12 ms. O ponto
@ 2.500 é o mais lento (cache frio); 200k é *mais rápido* que 2.5k, não
mais lento. EXPLAIN: `Index Scan using idx_transacoes_usuario_periodo_categoria`,
`actual rows=50`, `Execution Time: 0.146 ms`.

No H2 o índice aparece no EXPLAIN, mas o p95 ainda sobe ~3,4×. Planejador
H2. Por isso o manifesto **não** usa H2.

---

## Fase 0.3 — Postgres versus H2

Tabela completa em `docs/BASELINE_TOOLS.md`. Resumo do ponto @ 2.500:

| Capability | H2 p95 | PG p95 | manifesto |
|---|---:|---:|---:|
| `finance.accounts.list` | 22 | **3** | 3 |
| `finance.transactions.search` | 22 | **20** | 20 |
| `finance.invoice.read` | 27 | **15** | 15 |

Postgres desta rodada: **embedded 16** (Zonky), não o `consumo_db` da
máquina (porta 5432 existe, 5439/Docker não). Isolado de propósito.

---

## Fase 1 — contrato de capability

- `POST /api/capabilities/{id}:invoke` — JWT da sessão, `input` validado
  contra `required` do `input_schema`. Path `INSTANT`, `auth_scheme=session`.
- Angular manda `{ capability, input }` via `CapabilityService`
  (`encodeURIComponent(id) + ':invoke'`). Botões: `finance.cards.list` e
  `finance.month.summary`.
- `LocalFinanceCapabilityService` é a **única** implementação. `/api/ia-chat`
  só executa capability se o body ainda trouxer o ID (ponte).
- Métrica da ponte: `auth_scheme=legacy`. Entrada em `docs/COMPAT_DEBT.md`,
  remoção alvo **2026-12-07**.
- Renderizador por capability no painel J.A.R.V.I.S. (cartões, mês, contas,
  lançamentos, assinaturas, agendamentos, categorias, fatura). Não é JSON
  genérico.
- **Escape hatch:** texto livre **não** resolve mais por regex. Vai para a
  E.D.I.T.H. em `BALANCED` (interceptor + controller). Confirmado no teste
  `textoLivreNaoResolveCapabilityLocal`.

Não verifiquei o Angular no browser nesta máquina (app de dev não estava
no ar). Há spec dos chips em `jarvis-chat.util.spec.ts`.

---

## Fase 2 — manifesto

`GET /capabilities` e `GET /api/capabilities`, segredo de serviço
(`X-Eco-Service-Key` ou `X-API-Key` = `callback-secret`). JWT de usuário
**não** substitui o segredo (teste `jwtDeUsuarioNaoSubstituiSegredo`).
Sem segredo: `UNAUTHORIZED`.

Nove capabilities **implementadas** (allowlist read-only). Nenhuma write.
Todas com `sensitivity: FINANCIAL`, `execution: DETERMINISTIC`,
`local_resolvable: true`. `finance.month.summary` devolve estrutura
numérica, sem texto de modelo.

`p95_latency_ms` das três medidas = Postgres (tabela acima). As outras seis
estão no manifesto porque existem e têm teste; o p95 delas **não** teve
amostra Postgres dedicada nesta rodada — não usar para viabilidade de rota
até remedir. Aliases: 5–8 por capability, com forma sem acento e erro comum
(`meus cartoes`, `trasacoes`, `projecao de caixa`).

---

## Dívida (`docs/COMPAT_DEBT.md`)

| Item | Data-alvo |
|---|---|
| Envelope sintetizado no Tool Bridge (`envelope_synthesized=true`) | 2026-12-07 |
| HMAC seção 9 ainda não aplicado (`auth_scheme=legacy`) | 2026-11-07 |
| `X-Eco-User-Id` só depois do JWT | 2026-10-21 |
| WhatsApp não é borda | 2026-10-21 |
| `/api/ia-chat` como ponte de capability | 2026-12-07 |
| `finance.category.summary` agrega em memória | 2026-10-21 |

---

## O que esta rodada não fez (pedido)

- `finance:write` continua desabilitado (`EdithToolRegistry` rejeita
  `finance.transaction.create`).
- Sem provider/LLM/Ollama/OpenRouter daqui.
- WhatsApp: só dívida, sem instrumentar.
- GET SSE: sem kill de `Deadline-Total` no primeiro byte.

---

## Testes que fecharam a rodada

```
LocalFinanceCapabilityServiceTest
EdithToolRegistryTest
CapabilityManifestHttpTest
CapabilityAuthOwnershipHttpTest   (curl 401 + Service SCOPE_DENIED)
EdithToolBridgeHttpTest
EdithFeatureFlagHttpTest
ToolLatencyBaselineTest           (H2 @ 2.500)
ToolSearchScaleTest               (H2 2.5k–200k)
ToolPostgresBaselineTest          (PG 2.5k–200k + EXPLAIN)
```
