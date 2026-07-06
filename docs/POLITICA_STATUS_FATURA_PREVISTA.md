# Política — status PREVISTA em faturas (CF-11)

## Intencional

`PREVISTA` representa um **ciclo futuro** criado antes do fechamento real — tipicamente por:

- parcelas futuras de importação PDF;
- projeção agregada Itaú (quando não há parcelas individuais);
- `obterOuCriarFaturaParaVencimentoAlvo` ao registrar compras/parcelas.

## Limite comprometido

Faturas `PREVISTA` e `VENCIDA` entram no limite usado do cartão (mesma regra que `ABERTA`/`PARCIAL`).

## Sincronização de totais

Todo lançamento novo/atualizado/excluído vinculado a fatura dispara `FaturaService.sincronizarValorFaturaComTransacoes(faturaId)` — inclusive WhatsApp e importação PDF.
