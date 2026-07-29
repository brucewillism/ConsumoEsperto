# Migrations legadas (fora do classpath Flyway)

Estes scripts foram substituídos por `V1__baseline_inicial.sql` + `V2__schema_autopatch_complementar.sql`.

**Banco vazio:** use apenas as migrations em `db/migration/`.

**Banco legado com `flyway_schema_history` antigo:** não reaplique estes ficheiros automaticamente.
Adote baseline controlada (`flyway baseline`) ou migração manual conforme o ambiente.
