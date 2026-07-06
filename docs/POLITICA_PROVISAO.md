# Política de provisão (fonte única)

## Precedência (mais específica vence)

1. **DespesaFixa** cadastrada ou parcela real PREVISTO (empréstimo, parcelamento).
2. **PLANO_FUTURO** na memória J.A.R.V.I.S. com `mes_alvo` explícito.
3. Provisão **sazonal histórica** / `EVENTO_SAZONAL` inferido do histórico.

## Deduplicação

Quando duas fontes casam no **mesmo mês** com **valor aproximado** (tolerância **percentual** configurável, padrão **10%**, com piso absoluto opcional):

- Conta **uma vez** na disponibilidade e na Sentinela.
- Exemplo: estimativa "uns 1500" (PLANO_FUTURO) casa com sazonal R$ 1.487,35; R$ 900 não casa.
- Despesa fixa cadastrada vence inferência sazonal/memória.
- PLANO_FUTURO vence histórico sazonal quando colidem.

## Configuração

```properties
consumoesperto.provisao.tolerancia-dedup-pct=10
consumoesperto.provisao.tolerancia-dedup-piso=2.00
```

## Fixas no gráfico

Despesas fixas cadastradas aparecem **no dia de vencimento** (`mapaSaltosProjetadosAposDia`), não rateadas **e** no salto — evita dupla contagem no burn diário do gráfico "futuro provável".
