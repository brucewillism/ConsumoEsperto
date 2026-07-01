# Restore PostgreSQL — resultado da validação

**Data:** 2026-07-01  
**Ambiente do agente:** Windows, Docker **indisponível** (Testcontainers também pulado).

## Script validado

- `scripts/restore-postgres-backup.sh` recebeu suporte a `RESTORE_SKIP_CONFIRM=1` para CI/automação.
- Fluxo: confirmação interativa (ou skip) → `createdb` → `pg_restore` / `psql` / `gunzip -c`.

## Execução nesta máquina

Não foi possível executar restore end-to-end aqui porque:

1. Docker não está instalado/acessível no PATH.
2. Conexão local ao Postgres (`localhost:5432`) não respondeu com credenciais padrão.

## Como executar (humano / CI)

```bash
# 1. Gerar backup (exemplo)
pg_dump -h localhost -p 5439 -U consumo -Fc consumo_db > /tmp/test.dump

# 2. Restore em banco descartável
export PGHOST=localhost PGPORT=5439 PGUSER=consumo
export RESTORE_SKIP_CONFIRM=1
./scripts/restore-postgres-backup.sh /tmp/test.dump consumo_db_restore_test

# 3. Validar
psql -h $PGHOST -p $PGPORT -U $PGUSER -d consumo_db_restore_test -c "\dt"
```

No CI (`.github/workflows/backend-ci.yml`), adicionar job opcional com serviço `postgres:16` + passo acima.

## Aceite parcial

- Script revisado e flag não-interativa adicionada.
- **Execução real pendente** em ambiente com Postgres acessível (VPS ou CI com Docker).
