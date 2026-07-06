# Política de status de transação (PREVISTO × CONFIRMADO)

## Regra geral

| Contexto | Status incluídos | Rótulo na UI |
|----------|------------------|--------------|
| Médias, séries históricas, detecção de hábitos | **CONFIRMADA** apenas | "realizado" |
| Projeções futuras (safra, dashboard projetado, Sentinela) | CONFIRMADA + **PREVISTO** vincendas | "projeção" |

## Implementação

- `TransacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo` — agregações históricas.
- `SimulacaoCompraService`, `RecurringExpenseDetectionService`, `DashboardProjectionService.deltaDoDia` — só CONFIRMADA.
- Parcelas de empréstimo, fixas futuras, receitas fiscais PREVISTO — entram apenas em projeção, nunca em média passada.

## Exceções

- `PAGAMENTO_FATURA` confirmado impacta saldo e patrimônio (caixa), mas não entra em média de gasto de cartão na fatura.
- Receitas com `emprestimo_id` nunca entram em média de renda (não são salário).
