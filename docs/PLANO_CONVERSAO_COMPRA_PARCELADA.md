# Plano de convergência — CompraParcelada (CF-18)

O modelo `CompraParcelada` está **deprecated**. Novas criações via API estão bloqueadas.

## Direção

- **Fonte canônica:** transações de cartão com `grupoParcelaId`, `parcelaAtual` e `totalParcelas`, vinculadas a faturas (`PREVISTA` / `ABERTA`).
- **Importação PDF:** gera parcelas futuras idempotentes por chave natural.
- **Dados legados:** registros existentes em `compras_parceladas` permanecem para leitura e amortização sazonal até migração manual ou job futuro.

## Sem exclusão de dados

Nenhum dado histórico será apagado automaticamente. Migração futura (backlog) mapeará `CompraParcelada` → transações/faturas equivalentes.
