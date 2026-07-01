# Plano de migração SchemaAutoPatchService → Flyway (NÃO EXECUTAR EM PRODUÇÃO SEM BACKUP)

## Situação atual

- `spring.flyway.enabled=true` em `application.properties`, mas **não há** arquivos em `classpath:db/migration/`.
- O schema real evolui via `SchemaAutoPatchService` (`@PostConstruct`, DDL idempotente).
- Flyway faz baseline vazio; patches runtime preenchem lacunas — difícil reproduzir ambiente e rollback.

## Objetivo

Versionamento explícito **sem big-bang** e **sem recriar** banco existente.

## Fases propostas

### Fase 0 — Pré-requisitos (humano)

1. Backup completo: `pg_dump -Fc consumo_db > backup_pre_flyway.dump`
2. Validar restore em banco descartável (`scripts/restore-postgres-backup.sh`)
3. Congelar deploys durante janela de validação

### Fase 1 — Baseline (somente leitura do schema existente)

```bash
pg_dump --schema-only --no-owner --no-privileges \
  -h localhost -p 5439 -U consumo consumo_db \
  > backend/src/main/resources/db/migration/V1__baseline.sql
```

- Revisar manualmente o SQL (remover `CREATE EXTENSION` duplicados se necessário).
- Commitar `V1__baseline.sql` como **snapshot** do schema em produção na data do baseline.

Configuração:

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.baseline-description=Schema existente antes da migração formal
```

Em banco **já populado**, Flyway registra baseline e **não executa** V1 (nada é recriado).

### Fase 2 — Coexistência

- Manter `SchemaAutoPatchService` ativo como fallback idempotente.
- Novos objetos **somente** via `V2__...sql`, `V3__...sql`.
- Remover patch correspondente do `SchemaAutoPatchService` apenas após Flyway estável em staging.

### Fase 3 — Novas migrations

Exemplo:

```
V2__evento_webhook_processado.sql
V3__metas_valor_acumulado.sql
```

### Fase 4 — Desligar patches redundantes

Quando staging/produção confirmarem Flyway:

1. Listar patches duplicados no `SchemaAutoPatchService`
2. Remover um a um com deploy monitorado
3. Manter patches de emergência (extensão pgvector, etc.) até última fase

## Critérios de aceite

| Cenário | Resultado esperado |
|---------|-------------------|
| Banco novo (CI/local) | Flyway aplica V1+ e app sobe |
| Banco existente VPS | `flyway_schema_history` baseline; **zero** DROP/CREATE destrutivo |
| Rollback de código | Reverter JAR; schema permanece (migrations forward-only) |

## Riscos

- Corrida boot: Flyway + `SchemaAutoPatchService` no mesmo `@PostConstruct` — serializar (Flyway primeiro via `FlywayMigrationStrategy` ou `@DependsOn`).
- Drift: baseline desatualizado — regenerar baseline apenas em ambiente novo, nunca sobrescrever produção.

**Este plano não foi executado automaticamente. Requer validação humana e backup antes de qualquer passo em ambiente com dados.**
