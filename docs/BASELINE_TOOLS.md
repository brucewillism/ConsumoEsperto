# Baseline de latência das tools financeiras (rodada 2)

Medição **real**. Dois bancos, lado a lado. O número que entra no manifesto
(`GET /capabilities` → `p95_latency_ms`) é o **Postgres**, não o H2.

Data: 2026-09-07. Máquina: Windows 10, JDK 17.0.15 (Microsoft).

---

## 1. H2 in-memory (profile `test`)

| Item | Valor |
|---|---|
| Banco | H2 in-memory (`jdbc:h2:mem:testdb`) |
| Volume (baseline 3 tools) | 12 contas, **2 500** transações (120 dias), 24 faturas |
| Volume (curva search) | 2 500 → 50 000 → 200 000 transações do mesmo usuário |
| Amostras baseline | 20 warmup + **80** medições |
| Amostras curva | 8 warmup + 24 medições por ponto |
| Rede / HMAC / HTTP | zero (`tool.execute` / `executeForUser`) |

### Baseline @ 2.500 (após pushdown SQL)

Suíte `ToolLatencyBaselineTest`:

```
BASELINE accounts p50=4 p95=22 p99=44 | search p50=15 p95=22 p99=29 | invoice p50=7 p95=27 p99=43
```

| Capability | p50 | p95 | p99 |
|---|---:|---:|---:|
| `finance.accounts.list` | 4 | 22 | 44 |
| `finance.transactions.search` | 15 | 22 | 29 |
| `finance.invoice.read` | 7 | 27 | 43 |

### Curva `transactions.search` no H2

`ToolSearchScaleTest`, `limit=50`, período 200 dias, índice presente no EXPLAIN:

```
SCALE_H2 search | n=2500 p50=19 p95=43 p99=47 | n=50000 p50=44 p95=61 p99=62 | n=200000 p50=128 p95=146 p99=150
H2_RATIO p95_200k/p95_2500=3.40
```

O H2 **usa** `idx_transacoes_usuario_periodo_categoria`, mas o p95 ainda cresce
com o volume (planejador H2). Não é o número do manifesto.

---

## 2. Postgres (embedded 16, planejador real)

Isolado do `consumo_db`. Suíte `ToolPostgresBaselineTest`
(`io.zonky.test:embedded-postgres`). Schema Hibernate + índice **V10**
(`usuario_id, data_transacao DESC, categoria_id`), `ANALYZE` a cada volume.

### Baseline @ 2.500 (40 amostras)

```
PG_BASELINE n=2500 accounts p50=1 p95=3 p99=4 | search p50=9 p95=20 p99=24 | invoice p50=8 p95=15 p99=36
```

| Capability | p50 | p95 | p99 | `p95_latency_ms` (manifesto) |
|---|---:|---:|---:|---:|
| `finance.accounts.list` | 1 | 3 | 4 | **3** |
| `finance.transactions.search` | 9 | 20 | 24 | **20** |
| `finance.invoice.read` | 8 | 15 | 36 | **15** |

### Curva `transactions.search` no Postgres

```
PG_SCALE search n=50000 p50=7 p95=9 p99=10
PG_SCALE search n=200000 p50=10 p95=12 p99=12
```

| n transações | p50 | p95 | p99 |
|---:|---:|---:|---:|
| 2 500 | 9 | 20 | 24 |
| 50 000 | 7 | 9 | 10 |
| 200 000 | 10 | 12 | 12 |

p95 em 200k / p95 em 2.5k = **0,60**. A amostra @ 2.500 inclui cold cache
(20 ms); depois do warmup o p95 fica em **9–12 ms** até 200k. **Estável.**
Critério de aceite da Fase 0.2 cumprido no Postgres.

O manifesto publica **20** (p95 da amostra @ 2.500, o pior ponto medido), não 12:
a E.D.I.T.H. usa esse número para viabilidade de rota; pecar para cima é
mais seguro do que maquiar.

### EXPLAIN (Postgres, 200k linhas, `LIMIT 50`)

```
Limit  (cost=0.29..3.89 rows=50) (actual time=0.046..0.120 rows=50 loops=1)
  Buffers: shared hit=52
  ->  Index Scan using idx_transacoes_usuario_periodo_categoria on transacoes
        Index Cond: (usuario_id = $1 AND data_transacao >= $2 AND data_transacao <= $3)
        Filter: (NOT excluido)
Planning Time: 0.247 ms
Execution Time: 0.146 ms
```

O índice é usado. O scan devolve **50 linhas**, não 200 000. Tempo de execução
SQL ~0,15 ms; o restante do p95 é JPA (hydrate de 50 entidades + DTO).

---

## 3. H2 versus Postgres (diferença)

| Capability | H2 p95 @ 2.5k | PG p95 @ 2.5k | Δ (H2 − PG) |
|---|---:|---:|---:|
| `finance.accounts.list` | 22 | 3 | +19 ms |
| `finance.transactions.search` | 22 | 20 | +2 ms |
| `finance.invoice.read` | 27 | 15 | +12 ms |

A diferença que importa não é o ponto @ 2.500 do search (parecido). É a **curva**:

| n | H2 p95 search | PG p95 search |
|---:|---:|---:|
| 2 500 | 43 (curva) / 22 (baseline 80 amostras) | 20 |
| 50 000 | 61 | 9 |
| 200 000 | 146 | 12 |

H2 cresce ~3,4× entre 2,5k e 200k. Postgres **não**. Publicar o p95 do H2 no
manifesto faria a E.D.I.T.H. achar que `search` degrada com o histórico do
usuário. Não degrada, no planejador que importa.

---

## 4. O que mudou no `search` (Fase 0.2)

1. **Pushdown:** `SELECT id … ORDER BY data_transacao DESC LIMIT :n` nativo,
   depois hydrate só desses IDs (`findGraphByIdIn`). Sem `COUNT(*)` de `Page`.
2. **Teto:** default 50, máximo **100**. Acima → `INVALID_INPUT` (não trunca).
3. **Índice:** `idx_transacoes_usuario_periodo_categoria`
   `(usuario_id, data_transacao DESC, categoria_id)` — Flyway `V10` e
   `@Index` na entidade. Verificado no `EXPLAIN` acima.
4. **DTO:** `FinanceTransactionSearchItemDto` — nunca a entidade.
   Campos: `id`, `occurred_at`, `amount`, `type`, `category_id`, `category`,
   `account_id`, `card_id`, `description` (≤80). Fora: CNPJ, fingerprint,
   notas internas, usuário, auditoria.

---

## 5. Como reproduzir

```powershell
$env:JAVA_HOME = "C:\Users\bruce.silva\.jdks\ms-17.0.16"
.\scripts\mvn-backend.ps1 "-Dtest=ToolLatencyBaselineTest,ToolSearchScaleTest,ToolPostgresBaselineTest" test
```

Procure `BASELINE accounts`, `SCALE_H2`, `PG_BASELINE` / `PG_SCALE` / `PG_EXPLAIN`.
Artefatos: `backend/target/scale-h2.txt`, `backend/target/pg-baseline.txt`.

---

## 6. O que esta medição não inclui

Filtro HTTP, HMAC, JSON, rede Angular ↔ backend. WhatsApp não é borda.
`finance.category.summary` ainda agrega o mês em memória (dívida em
`docs/COMPAT_DEBT.md`).
