# Sanitização de dados legados (v8.3)

O patch **`v8.3-conciliacao-retroativa-salarial-fiscal`** roda **automaticamente** no startup do backend (via `SchemaAutoPatchService` → `DadosLegadosSanitizationService`).

## O que corrige

| Problema | Ação |
|----------|------|
| 13º recebido sem `origem_fiscal` | Marca `DECIMO_TERCEIRO_*` |
| Restituição IR sem flag | Marca `RESTITUICAO_IR` |
| PIX/transferência na categoria Salário | Move para **Outras receitas** |
| Holerite/automático sem categoria Salário | Vincula categoria **Salário** |
| Duplicata contracheque + automático | Poda via `SalarioAutomaticoService` (12 meses) |

## Deploy VPS

```bash
cd /opt/consumoesperto
git pull origin main
docker compose build backend
docker compose up -d backend
docker compose logs backend | grep -i "data patch v8.3"
```

Confirme no log: `Data patch v8.3-conciliacao-retroativa-salarial-fiscal concluído`.

## Reexecutar o patch (se necessário)

```sql
DELETE FROM schema_data_patches
WHERE patch_id = 'v8.3-conciliacao-retroativa-salarial-fiscal';
```

Reinicie o backend.

## Diagnóstico manual (Postgres)

Substitua o e-mail e rode no container do Postgres:

```sql
-- 13º sem origem_fiscal
SELECT t.id, t.descricao, t.valor, t.data_transacao
FROM transacoes t
JOIN usuarios u ON u.id = t.usuario_id
WHERE u.email = 'bruce.willis.br07@gmail.com'
  AND t.excluido = false AND t.tipo_transacao = 'RECEITA'
  AND t.origem_fiscal IS NULL
  AND LOWER(COALESCE(t.descricao, '')) LIKE '%13%sal%';

-- Salários duplicados por mês
SELECT date_trunc('month', t.data_transacao) AS mes, COUNT(*)
FROM transacoes t
JOIN usuarios u ON u.id = t.usuario_id
LEFT JOIN categorias c ON c.id = t.categoria_id
WHERE u.email = 'bruce.willis.br07@gmail.com'
  AND t.excluido = false AND t.tipo_transacao = 'RECEITA'
  AND t.origem_fiscal IS NULL
  AND (LOWER(COALESCE(t.descricao, '')) LIKE '%salario%'
       OR LOWER(COALESCE(c.nome, '')) IN ('salário', 'salario'))
GROUP BY 1 HAVING COUNT(*) > 1;

-- Patch aplicado?
SELECT * FROM schema_data_patches
WHERE patch_id = 'v8.3-conciliacao-retroativa-salarial-fiscal';
```

Cópia local (gitignore): `scripts/sql/sanitizacao-dados-v8.3.sql`
