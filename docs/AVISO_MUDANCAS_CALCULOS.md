# Aviso ao usuário — cálculos mais precisos

Use este texto (ou adapte) no dashboard e no WhatsApp **antes** do deploy dos Lotes 1–2.

---

Atualizamos a forma como o ConsumoEsperto calcula patrimônio, projeções e médias — os números ficaram mais fiéis à sua vida financeira real.

Se você tem empréstimo consignado, o **patrimônio líquido pode aparecer menor**: agora descontamos as parcelas que ainda faltam pagar, não só o dinheiro que entrou na conta. Isso é o correto — o crédito do empréstimo não é renda.

As **projeções do dashboard** passam a considerar salário e receitas esperadas no mês, não só o gasto médio. A **safra de meses futuros** inclui contas fixas, faturas e parcelas de empréstimo, não apenas um “burn” genérico.

**Médias e gráficos do que já aconteceu** usam só transações **confirmadas**; lançamentos previstos entram apenas nas projeções, com rótulo de projeção.

Nenhuma transação foi apagada — só a forma de somar mudou. Se algo parecer estranho, confira empréstimos e despesas fixas cadastradas.

---

## Lotes 3–6 (jul/2026)

- Pagamento de fatura bloqueado no CRUD genérico; use fluxo de pagamento ou quitação `EXTERNA`.
- Limite WhatsApp inclui faturas PREVISTA/VENCIDA.
- Simulação de compra: média por meses com dado (não `/6` fixo).
- Burn Sentinela: divisor = dias reais da janela.
- Metas: renda de `RendaConfig`; aviso se soma >100% (sem bloqueio).
- Fiscal: estimativa simplificada; obrigações vencidas → ano+1.
- Empréstimo: `descontoEmFolha` default true (consignado).

---
