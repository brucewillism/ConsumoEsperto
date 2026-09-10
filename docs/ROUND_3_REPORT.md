# Relatório — rodada 3 (ConsumoEsperto)

2026-09-08. Sem commit/push. Contrato `docs/ECOSYSTEM_CONTRACT.md`: **v1.1**,
`Deadline-First-Token` presente. **Não alterei o contrato.** `finance:write`
continua desabilitado. Sem provider/LLM. WhatsApp não foi instrumentado.

---

## Fase 2.1 — Onde estão os 19,8 ms?

O SQL de IDs do search continua ~0,15 ms (EXPLAIN da rodada 2). O p95
publicado no manifesto é 20 ms. A diferença é custo **fixo**, não crescimento
com volume.

Instrumentação (`CapabilityStageClock`): nomes canônicos `t_tool_ms`,
`t_total_ms`, `t_unaccounted_ms` e sufixos `t_pool_acquire_ms`,
`t_sql_ids_ms`, `t_jpa_hydrate_ms`, `t_dto_map_ms`, `t_json_ms`,
`t_framework_ms`, mais `t_ownership_ms` / `t_reconcile_ms` / `t_jpa_items_ms`
onde cabia. Relógio **desligado** em produção. **Não otimizei.**

Suíte: `ToolStageBreakdownTest` (Postgres embedded, n=2500, isolado do
`consumo_db`). Artefato: `backend/target/stage-breakdown.txt`.

Números medidos nesta máquina (Postgres embedded 16, n=2 500, 12 warmup +
32 amostras nas stages; JSON 8+16; HTTP invoke 4+12). Artefato
`backend/target/stage-breakdown.txt`. Locale da JVM imprimiu vírgula.

### `finance.transactions.search` (o 19,8 ms)

| Estágio | p50 (ms) | p95 (ms) |
|---|---:|---:|
| `t_tool_ms` | 18,50 | 41,37 |
| `t_pool_acquire_ms` | 0,080 | 0,254 |
| `t_ownership_ms` | 0,002 | 0,002 |
| `t_sql_ids_ms` | 2,505 | 11,268 |
| **`t_jpa_hydrate_ms`** | **11,952** | **32,912** |
| `t_dto_map_ms` | 0,377 | 0,887 |
| `t_unaccounted_ms` | — | 6,558 |
| `t_json_ms` (ObjectMapper) | — | 4,334 |
| `t_total_ms` HTTP invoke | 35,45 | 41,72 |
| `t_framework_ms` (p95 HTTP − p95 tool) | — | 0,35 |

**Dominante: `t_jpa_hydrate_ms`** (`findGraphByIdIn` + `@EntityGraph` de
categoria, conta, fatura, cartão nas 50 linhas).

O SQL do EXPLAIN da rodada 2 (0,146 ms) continua sendo o planejador. O
`t_sql_ids_ms` (p95 11 ms) é a mesma query **pelo Spring Data** (bind,
sessão, mapear `List<Long>`). Ainda é ~⅓ do hydrate, não o vilão.

Pool ~0,25 ms. Map DTO < 1 ms. JSON ~4 ms. Framework HTTP, nesta suíte,
some no p95 do search (o tool já é o grosso). No **p50**, HTTP 35 ms vs
tool 18 ms → ~17 ms de filtro/segurança/serialização no MockMvc — piso
parecido com o p50 HTTP de `accounts.list` (17,6 ms).

Este p95 de tool (41 ms) é **pior** que os 20 ms publicados: a sonda de
pool (get+close extra) e o relógio só ligam no teste; a amostra é outra.
Não atualizei o manifesto. O ponto da fase era **qual etapa domina**, não
um novo p95.

### `finance.accounts.list` (os 3 ms)

| Estágio | p50 (ms) | p95 (ms) |
|---|---:|---:|
| `t_tool_ms` | 2,42 | 8,03 |
| `t_pool_acquire_ms` | 0,079 | 0,159 |
| **`t_jpa_hydrate_ms`** | **2,192** | **7,793** |
| `t_dto_map_ms` | 0,047 | 0,089 |
| `t_json_ms` | — | 0,234 |
| `t_total_ms` HTTP | 17,60 | 210,98 |

**Dominante no tool: `t_jpa_hydrate_ms`.** O p95 HTTP 211 ms é outlier de
primeira chamada MockMvc nesta suíte (p50 HTTP 17,6). Não leia 203 ms de
framework como custo estável.

### `finance.invoice.read` (os 15 ms)

| Estágio | p50 (ms) | p95 (ms) |
|---|---:|---:|
| `t_tool_ms` | 14,18 | 20,96 |
| `t_pool_acquire_ms` | 0,059 | 0,249 |
| `t_jpa_hydrate_ms` | 2,681 | 5,695 |
| `t_reconcile_ms` | 4,246 | 8,732 |
| **`t_dto_map_ms`** | **3,591** | **9,532** |
| `t_jpa_items_ms` (dentro do dto_map) | 2,414 | 5,274 |
| `t_json_ms` | — | 0,283 |
| `t_total_ms` HTTP | 15,58 | 37,05 |

**Dominante: `t_dto_map_ms`** (`converterParaDTO`, ainda carrega
lançamentos da fatura porque o método é compartilhado com a UI).
`t_reconcile_ms` vem logo atrás. A tool **não devolve** mais as linhas
(`item_count`); o serviço ainda as busca. Não fatiei isso — seria
otimizar o 15 ms.

### Framework ou lógica?

Nas três, o **pool é irrelevante**. O piso HTTP aquecido é da ordem de
15–17 ms no p50 do MockMvc, não 20 ms de tool. O que separa accounts (tool
~2–8 ms) de search (tool ~18–41 ms) é a **lógica/JPA**: grafo de 50
entidades vs uma lista curta de contas. Invoice paga reconciliação +
converter, não o framework.

Não otimizei. O próximo corte, se houver, é o hydrate do grafo — não o
SQL de IDs e não o envelope.

---

## Fase 2.2 — Matriz de degradação

Documento: `docs/DEGRADATION_MATRIX.md`.

Texto livre com a E.D.I.T.H. **fora**:

- fallback **ligado** (default) → `LegacyCognitiveGateway`, `mode=LEGACY`,
  badge **Modo degradado**, resposta **não vazia**.
- fallback **desligado** → `mode=DEGRADED`, frase explícita de
  indisponibilidade, finanças no app. Sem silêncio e sem 500 genérico.
- flag **off** → o mesmo gateway, `mode=LOCAL`, badge **Modo local**.

O `LegacyCognitiveGateway` não é código morto:
`EdithFeatureFlagHttpTest.edithDesligada_textoLivreRespondeViaLegacyGateway`
e `LegacyCognitiveGatewayHttpTest`.

Núcleo financeiro **não** depende da E.D.I.T.H. (dashboard/CRUD, faturas,
contas, relatório mensal, importação pendente). Teste
`nucleoFinanceiroNaoDependeDaEdith`. Nenhum defeito de arquitetura
encontrado nesses caminhos.

Mensagem: faixa discreta no painel quando `DEGRADED` /
`EDITH_UNAVAILABLE`; SSE falho usa a mesma frase de indisponibilidade, não
o erro genérico de “núcleo de inferência”.

---

## Fase 3.1 — Superfície

Detalhe campo a campo: `docs/CAPABILITY_SURFACE.md`.

Nenhuma das nove devolve entidade. Teto rígido em toda busca/lista
(`INVALID_INPUT` acima do máx).

| Capability | Saiu / mudou |
|---|---|
| `accounts.list` | `nome` untrusted; saiu `padrao` |
| `cards.list` | `nome`/`banco` untrusted |
| `transactions.search` | `description`/`category` = `{value, untrusted}` |
| `invoice.read` | saiu `principais_itens`; entrou `item_count` |
| `month.summary` | sem mudança de forma (já agregado) |
| `category.summary` | **SQL GROUP BY** (não mais heap); `category_id` |
| `subscriptions.list` | `nome` untrusted |
| `recurring.list` | `beneficiario` untrusted |
| `cashflow.project` | sem mudança de forma (já agregado) |

Agregado antes de linha: **mudaram** `category.summary` (implementação) e
`invoice.read` (saída). Os demais já eram totais ou lista com teto.

---

## Fase 3.2 — Injeção via dado

`UntrustedText` + `PromptSanitize` em todo campo de origem humana/PDF/CSV.
ADR: `docs/ADR-002-prompt-injection.md` (o que a marca resolve, o que não
resolve, o que a E.D.I.T.H. precisa respeitar). Sem `finance:write`.

---

## Fase 3.3 — Auditoria

Tabela `edith_tool_audit` (Flyway `V202609081200000`). Toda invocação do
Tool Bridge grava `trace_id`, `tool_call_id`, `user_id`, capability,
parâmetros **allowlist** (ids/datas/limites — sem descrição, sem valor, sem
nome), `row_count`, `duration_ms`, `outcome`. Sem coluna de payload.

Retenção: **90 dias**, purge `0 20 4 * * ?` America/Sao_Paulo
(`consumoesperto.edith.tool-audit.retention-days`). O log
`edith_tool_executed` também não imprime o `result`.

---

## Dívida (`docs/COMPAT_DEBT.md`)

Nenhuma data-alvo passou (a mais próxima é 2026-10-21).

| Item | Nesta rodada |
|---|---|
| Envelope sintetizado no Tool Bridge | Intacta. Aceitamos `X-Eco-*` se chegar; a E.D.I.T.H. ainda chama com `X-Edith-*` sem trace. Alvo 2026-12-07 |
| HMAC seção 9 | Intacta. Alvo 2026-11-07 |
| `X-Eco-User-Id` só após JWT | Intacta. Alvo 2026-10-21 |
| WhatsApp não é borda | Intacta (pedido: não instrumentar). Alvo 2026-10-21 |
| `/api/ia-chat` ponte `auth_scheme=legacy` | Intacta. Alvo 2026-12-07 |
| `category.summary` em memória | **Resolvido** (GROUP BY) |

---

## Como reproduzir o breakdown

```powershell
$env:JAVA_HOME = "C:\Users\bruce.silva\.jdks\ms-17.0.16"
.\scripts\mvn-backend.ps1 "-Dtest=ToolStageBreakdownTest,PromptSanitizeTest,EdithToolAuditServiceTest,LegacyCognitiveGatewayHttpTest,EdithFeatureFlagHttpTest,EdithToolBridgeHttpTest,CapabilityAuthOwnershipHttpTest,EdithToolRegistryTest,LocalFinanceCapabilityServiceTest,CognitiveGatewaySelectorTest" test
```
