# Política de provisão (fonte única)

## Precedência (mais específica vence)

1. **DespesaFixa** cadastrada ou parcela real PREVISTO (empréstimo, parcelamento).
2. **PLANO_FUTURO** na memória J.A.R.V.I.S. com `mes_alvo` explícito.
3. Provisão **sazonal histórica** / `EVENTO_SAZONAL` inferido do histórico.

## Deduplicação

Quando duas fontes casam no **mesmo mês** com **valor aproximado** (tolerância configurável, padrão R$ 2,00 via `consumoesperto.provisao.tolerancia-dedup`):

- Conta **uma vez** na disponibilidade e na Sentinela.
- Despesa fixa cadastrada vence inferência sazonal/memória.
- PLANO_FUTURO vence histórico sazonal quando colidem.

## Fixas no gráfico

Despesas fixas cadastradas aparecem **no dia de vencimento** (`mapaSaltosProjetadosAposDia`), não rateadas **e** no salto — evita dupla contagem no burn diário do gráfico "futuro provável".

## Configuração

```properties
consumoesperto.provisao.tolerancia-dedup=2.00
```
