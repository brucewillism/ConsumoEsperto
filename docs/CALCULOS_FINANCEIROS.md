# Cálculos financeiros — referência

Como o ConsumoEsperto soma saldos, patrimônio, projeções e provisões.  
**Última revisão:** setembro/2026 · Mudanças visíveis ao utilizador: [`AVISO_MUDANCAS_CALCULOS.md`](AVISO_MUDANCAS_CALCULOS.md)

Visões Mensal × Geral (taxonomia de obrigações, parcela do mês vs saldo devedor): [`POLITICA_VISOES_DASHBOARD.md`](POLITICA_VISOES_DASHBOARD.md).

---

## 1. Patrimônio líquido (card do dashboard)

**Fórmula:**

```
Patrimônio líquido = saldo em contas − passivo de empréstimo (todas as parcelas PREVISTO ativas)
```

- **Ativos:** soma de `conta_bancaria.saldo_atual` (contas ativas) ou saldo derivado de transações confirmadas (modo legado sem multicarteira).
- **Passivo:** soma de despesas `PREVISTO` com `emprestimo_id` preenchido — **inclui** consignado com `descontoEmFolha = true`. A flag controla apenas o débito em conta no fluxo de caixa, não a existência da dívida.
- **Não inclui:** faturas de cartão pendentes, despesas fixas do mês, provisões fiscais.

**Serviço:** `SaldoService.patrimonioLiquido()` · no dashboard Geral via `DashboardViewService`. O alias `saldoContaCorrente()` é **saldo em conta** (liquidez), não património.

---

## 2. Disponibilidade real (Sentinela — WhatsApp / job dia 5)

**Fórmula distinta do patrimônio (sem dupla contagem de empréstimo):**

```
Disponível = saldo em conta − fixas restantes no mês − restante de faturas pendentes − parcelas de empréstimo que debitam conta neste mês
```

- **Base:** `SaldoService.saldoContaCorrente()` / `saldoEmConta()` — soma das contas ativas (não desconta o passivo total de empréstimos).
- **Obrigações de caixa:** despesas fixas ainda não lançadas + restante de faturas (pagamento parcial reduz) + parcelas `emprestimoId` com `descontoEmFolha=false` no mês.
- **Não usa** património líquido nem saldo devedor integral. Consignado em folha não sai de caixa.

**Serviço:** `PrevisaoFluxoCaixaService.calcularDisponibilidadeReal()` → `SafeToSpendService`.

---

## 3. Projeção do mês / safra (M, M+1, M+2)

**Ponto de partida:** liquidez atual (saldo em conta hoje), ou saldo de caixa cascata do mês anterior. **Não** usa património líquido.

```
Projeção de caixa no fechamento do mês =
  liquidez atual
+ entradas ainda não realizadas até o fim do mês
− saídas ainda não realizadas até o fim do mês
− variável Anti-Susto aplicável
```

O saldo atual já incorpora eventos **CONFIRMADA**. Não somar de novo salário já recebido nem despesa já paga.

**Saídas restantes do mês** (`ComposicaoProjecaoMesService`) — cada obrigação económica entra **uma vez**:

- Despesas fixas ainda não lançadas neste mês (exclui DESPESA CONFIRMADA com a mesma descrição / prefixo `Despesa fixa:`)
- Restante de faturas com vencimento no mês (`valorFatura − valorPago`; PAGA/CANCELADA fora). Parcelas de compra no cartão (`grupoParcelaId`) **já estão na fatura** — não somam à parte.
- Parcelas de empréstimo `PREVISTO` no mês com `emprestimoId` **que debitam conta** (`descontoEmFolha=false`). Consignado em folha = 0 no caixa.
- Variável Anti-Susto: estimativa de gasto variável ainda não realizado (`sumDespesaVariavelConfirmadaPeriodo` × dias restantes, com margem antes do dia limiar). Não reapresenta fatura, fixa, empréstimo nem parcela de cartão.

**Entradas restantes:** gap salarial (`renda − receitas salariais CONFIRMADA`) + receitas fiscais ainda PREVISTO.

Assinaturas e agendamentos **não** entram no número da projeção (o cadastro de despesa fixa é a obrigação). Se o agendamento nasceu da própria fixa, não há dupla contagem.

**Serviço:** `SaldoService.calcularProjecaoMes` / `calcularProjecaoSafra` — mesma fórmula para dashboard Mensal, Sentinela, briefs, alertas e J.A.R.V.I.S. O protocolo de cautela dispara se **esta** projeção de caixa for negativa, não se o património líquido for negativo.

---

## 4. Gráfico «Trajetória de caixa» (Sentinela)

- Linha sólida: saldo em conta hoje (liquidez).
- Linha tracejada: visualização dia a dia (burn). O **saldo de fechamento** e o protocolo de cautela vêm de `SaldoService.calcularProjecaoMes`.
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

- ABERTA / PARCIAL / VENCIDA / PREVISTA: restam `valorFatura − valorPago` nas projeções de caixa (PAGA e CANCELADA não entram).
- PAGA: quitada; se o débito (`PAGAMENTO_FATURA`) já ocorreu, o saldo já caiu — não reprojeta a fatura.
- Pagamento parcial: projeta só o restante (nunca fatura + pago).
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
