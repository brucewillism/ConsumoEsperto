# Cálculos financeiros — referência

Como o ConsumoEsperto soma saldos, patrimônio, projeções e provisões.  
**Última revisão:** julho/2026 · Mudanças visíveis ao utilizador: [`AVISO_MUDANCAS_CALCULOS.md`](AVISO_MUDANCAS_CALCULOS.md)

---

## 1. Patrimônio líquido (card do dashboard)

**Fórmula:**

```
Patrimônio líquido = saldo em contas − passivo de empréstimo (todas as parcelas PREVISTO ativas)
```

- **Ativos:** soma de `conta_bancaria.saldo_atual` (contas ativas) ou saldo derivado de transações confirmadas (modo legado sem multicarteira).
- **Passivo:** soma de despesas `PREVISTO` com `emprestimo_id` preenchido — **inclui** consignado com `descontoEmFolha = true`. A flag controla apenas o débito em conta no fluxo de caixa, não a existência da dívida.
- **Não inclui:** faturas de cartão pendentes, despesas fixas do mês, provisões fiscais.

**Serviço:** `SaldoService.patrimonioLiquido()` · exposto no dashboard via `PrevisaoFluxoCaixaService.buildPrevisaoFuturoChart().saldoAtual` (alias `saldoContaCorrente`).

---

## 2. Disponibilidade real (Sentinela — WhatsApp / job dia 5)

**Fórmula distinta do patrimônio (sem dupla contagem de empréstimo):**

```
Disponível = saldoContaCorrente − fixas restantes no mês − faturas de cartão pendentes
```

- **Base:** `SaldoService.saldoContaCorrente()` — hoje equivale a **patrimônio líquido** (liquidez em contas já líquida do passivo total de empréstimos).
- **Obrigações do mês:** despesas fixas cadastradas/detectadas + faturas de cartão pendentes.
- **Empréstimos:** parcelas **não** entram nas «fixas» desta fórmula; o passivo já foi descontado na base. Parcelas que debitam conta (`descontoEmFolha = false`) entram na **projeção mensal** (`ComposicaoProjecaoMesService`), não aqui.

**Serviço:** `PrevisaoFluxoCaixaService.calcularDisponibilidadeReal()`.

---

## 3. Projeção do mês / safra (M, M+1, M+2)

**Ponto de partida:** patrimônio líquido (ou saldo cascata do mês anterior).

**Despesas previstas do mês** (`ComposicaoProjecaoMesService`):

- Despesas fixas restantes (vencimento no mês)
- Faturas de cartão com vencimento no mês
- Parcelas de empréstimo `PREVISTO` no mês **que debitam conta** (exclui desconto em folha)
- Gasto variável (burn rate × dias restantes, com margem anti-susto após dia 15)

**Receitas:** salário configurado (`RendaConfigService`) + receitas fiscais previstas (13º/IR).

**Serviço:** `SaldoService.calcularProjecaoMes` / `calcularProjecaoSafra`.

---

## 4. Gráfico «Trajetória de caixa» (Sentinela)

- **Linha sólida:** patrimônio líquido hoje.
- **Linha tracejada:** projeção dia a dia até fim do mês (burn + rateio de faturas + saltos de fixas + provisões de memória).
- Losangos âmbar: vencimento de despesa fixa cadastrada.

---

## 5. Empréstimo consignado

| Aspecto | Comportamento |
|---------|----------------|
| Crédito na conta | RECEITA CONFIRMADA (entra no saldo) |
| Parcelas | DESPESA PREVISTO, uma por mês |
| `descontoEmFolha = true` (padrão) | Não debita conta no fluxo; **entra** no passivo do patrimônio |
| `descontoEmFolha = false` | Debita conta no fluxo normal; entra no passivo e na projeção mensal de caixa |

Registo: WhatsApp (`EmprestimoService`) · cancelamento estorna crédito acumulado.

---

## 6. Faturas de cartão

| Status | Significado |
|--------|-------------|
| ABERTA / PARCIAL / VENCIDA | Compromete limite e projeções |
| PREVISTA | Ciclo futuro intencional — ver [`POLITICA_STATUS_FATURA_PREVISTA.md`](POLITICA_STATUS_FATURA_PREVISTA.md) |
| PAGA | Quitada; `PAGAMENTO_FATURA` ou `origemQuitacao = EXTERNA` (sem débito em conta) |

- Pagamento via app: `FaturaConciliacaoService` cria `PAGAMENTO_FATURA` + debita conta.
- CRUD genérico de transações **bloqueia** `PAGAMENTO_FATURA` — use fluxo de fatura.
- Importação PDF: dedup por descrição normalizada + data ±1 dia + valor.

---

## 7. Provisões e deduplicação

Precedência: **DespesaFixa / parcela real** → **PLANO_FUTURO (memória)** → **sazonal histórico**.

Dedup entre fontes no mesmo mês: tolerância **10%** + piso **R$ 2,00** (`consumoesperto.provisao.tolerancia-dedup-pct` / `piso`).

Ver [`POLITICA_PROVISAO.md`](POLITICA_PROVISAO.md).

---

## 8. Renda e metas

- **Média móvel de renda:** `RendaConfigService` — janela 90 dias, normalizada para 30; **exclui** créditos de empréstimo (`emprestimo_id IS NULL`).
- **Metas:** percentual sobre renda estimada; **progresso por valor acumulado** (`valorAcumulado` / `valorTotal`), não por tempo decorrido; soma >100% gera **aviso** (não bloqueia).
- **Fiscal:** estimativa simplificada — rótulo em todas as saídas; obrigações vencidas projetam para ano+1.

---

## 9. Integridade de saldo

Toda mutação de saldo: `SaldoMovimentacaoService` + lock `FOR UPDATE` + `movimentacao_saldo_log`.

Ver [`INTEGRIDADE_SALDO.md`](INTEGRIDADE_SALDO.md).

---

## 10. Políticas relacionadas

| Documento | Tema |
|-----------|------|
| [`POLITICA_STATUS_TRANSACAO.md`](POLITICA_STATUS_TRANSACAO.md) | CONFIRMADA vs PREVISTO |
| [`POLITICA_PROVISAO.md`](POLITICA_PROVISAO.md) | Dedup e precedência |
| [`POLITICA_STATUS_FATURA_PREVISTA.md`](POLITICA_STATUS_FATURA_PREVISTA.md) | Faturas PREVISTA |
| [`TIMEZONE_EXCECOES.md`](TIMEZONE_EXCECOES.md) | Fuso America/Sao_Paulo |
| [`AVISO_MUDANCAS_CALCULOS.md`](AVISO_MUDANCAS_CALCULOS.md) | Texto para o utilizador antes do deploy |
