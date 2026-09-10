# Superfície das nove capabilities (rodada 3)

Nenhuma tool devolve entidade JPA. Campo que existia “porque estava no DTO”
saiu. Teto rígido: pedido acima do máximo → `INVALID_INPUT`, sem truncar.

Campos `untrusted` = origem do usuário ou de importação PDF/CSV. Formato
`{"value":"...","untrusted":true}`.

---

## `finance.accounts.list`

Teto: default 20, máx 50.

| Campo | Por quê |
|---|---|
| `id` | Referência no app |
| `nome` | Untrusted. Apelido que o usuário digitou |
| `tipo` | Enum de sistema (CORRENTE/…) |
| `ativa` | Filtro de carteira viva |
| `saldo_disponivel` | O que a pergunta “quanto tenho” precisa |
| `total`, `limit`, `limit_max` | Paginação honesta |

Saiu: `padrao` (o app já sabe a conta padrão; não responde a pergunta da tool).

---

## `finance.cards.list`

Teto: 20 / 50.

| Campo | Por quê |
|---|---|
| `id` | Referência |
| `nome` | Untrusted |
| `banco` | Untrusted (digitado). Distingue dois cartões |
| `limite_disponivel` | Pergunta de limite |
| `dia_vencimento` | Pergunta de vencimento |
| `ativo` | Status |

Saiu: número do cartão (já estava fora).

---

## `finance.transactions.search`

Teto: default 50, máx 100. Filtros e `LIMIT` no SQL.

| Campo | Por quê |
|---|---|
| `id` | Abrir no app |
| `occurred_at`, `amount`, `type` | Recorte |
| `category_id` | Filtro, id de sistema |
| `category` | Untrusted (nome) |
| `account_id`, `card_id` | Origem, sem saldo/PAN |
| `description` | Untrusted, ≤80, sanitizado |

Saiu: CNPJ, fingerprint, notas internas, usuário, auditoria.

---

## `finance.invoice.read`

Um recurso. Sem teto de busca. **Sem linhas.**

| Campo | Por quê |
|---|---|
| `id` | Recurso |
| `cartao` | Untrusted (nome do cartão) |
| `competencia` | Untrusted (número/competência do PDF ou do usuário) |
| `status`, `paga` | Estado |
| `valor_total`, `valor_minimo`, `valor_pago` | Totais |
| `vencimento`, `fechamento`, `data_pagamento` | Agenda |
| `item_count` | Cardinalidade, não dump |

Saiu: `principais_itens` (era dump de até 20 lançamentos; a pergunta “como
está a fatura” é agregado).

---

## `finance.month.summary`

Já era agregado (`SUM` no SQL). Sem teto de linhas.

`competencia`, `total_receitas`, `total_despesas`, `total_investimentos`,
`fluxo_mes`, `saldo`, `saldo_projetado_fim_mes`, `total_transacoes`.

Nenhum campo untrusted (números de sistema).

---

## `finance.category.summary`

**Mudou de implementação:** `GROUP BY` no SQL
(`sumDespesasPorCategoriaCapability`). Antes: `buscarPorPeriodo` + soma no
heap.

Teto: default 12, máx 30.

| Campo | Por quê |
|---|---|
| `category_id` | Id de sistema, pode ser nulo |
| `categoria` | Untrusted (nome) |
| `total` | Soma |

Sem linhas de transação.

---

## `finance.subscriptions.list` / `finance.recurring.list`

Teto: 20 / 50.

Assinatura: `id`, `nome` (untrusted), `valor`, `dia_vencimento`, `ativo`.

Agendamento: `id`, `beneficiario` (untrusted), `valor`, `vencimento`,
`status`, `recorrencia` (enum).

---

## `finance.cashflow.project`

Já era agregado do motor interno. Sem LLM.

`saldo_previsto`, `despesas_previstas`, `receitas_previstas`,
`chance_mes_positivo_pct`, `explicacao` (texto **nosso**, determinístico —
não untrusted), `calculado_em`.

---

## O que mudou de saída nesta rodada

| Capability | Mudança de saída |
|---|---|
| `accounts.list` | `nome` untrusted; saiu `padrao`; `limit`/`limit_max` |
| `cards.list` | `nome`/`banco` untrusted; teto documentado no payload |
| `transactions.search` | `description`/`category` passam a objeto untrusted |
| `invoice.read` | saiu `principais_itens`; entrou `item_count`; nomes untrusted |
| `month.summary` | nenhuma (já agregado) |
| `category.summary` | SQL GROUP BY; `category_id`; `categoria` untrusted; teto 12/30 |
| `subscriptions.list` | `nome` untrusted |
| `recurring.list` | `beneficiario` untrusted |
| `cashflow.project` | nenhuma |

Agregado-antes-de-linha: `category.summary` deixou de carregar transações;
`invoice.read` deixou de devolver linhas. Os outros já eram lista curta
com teto ou já eram agregado.
