# Plano de migração SchemaAutoPatchService → Flyway

## Situação actual (2026-09-09)

Flyway **está ligado** em produção/dev:

```
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=false
```

Há `V1__baseline_inicial.sql` (dump Hibernate da época, **não** um `pg_dump` de produção) e incrementais `V2`…`V10` mais versões datadas.

Isto **não** é a Fase 1 do plano original (baseline com `pg_dump` de produção + `baseline-version=1` + não executar V1). O `baseline-on-migrate` actual é `0`: em base **já populada sem** `flyway_schema_history`, o Flyway marca 0 e depois tenta aplicar V1… — V1 **não** é `IF NOT EXISTS` em todas as tabelas. Risco operacional separado; não reabrir big-bang aqui.

Perfis:

| Perfil | Flyway | Autopatch | Hibernate DDL |
|--------|--------|-----------|---------------|
| default / produção | on | on (legado) | `none` |
| `integracao` | on | **off** | `validate` |
| testes H2 | off | off | `create` |

## Inventário — um dono por objecto

### Só Flyway (autopatch **não** cria)

| Migration | Objectos |
|-----------|----------|
| `V2__schema_autopatch_complementar.sql` | `evento_webhook_processado`, `memoria_semantica_jarvis`, `transacao_semantica_index`, `jarvis_feedback` (+ `data_expiracao`), `compra_parcelada_migracao_controle`, `CREATE EXTENSION vector` (best-effort) |
| `V3` / catch-up | `ux_faturas_cartao_competencia_nao_quitada` |
| `V4` / catch-up | `usuarios.role` |
| `V5` | `grupo_familiar_membros.papel` |
| `V6` / catch-up | `agendamento_execucoes` + unicidade |
| `V7` / catch-up | `edith_conversation_link`, `edith_task_link`, `edith_callback_nonce` |
| `V8`–`V9` / catch-up | captura móvel, colunas de ingestão em `transacoes` |
| `V10` | `idx_transacoes_usuario_periodo_categoria` |
| `V202609081200000__edith_tool_audit.sql` | `edith_tool_audit` (**sem** `IF NOT EXISTS` — primeira aplicação; se a tabela já existir *sem* histórico Flyway desta versão, a migrate falha de propósito) |
| `V202609091200000__whatsapp_conexao_status.sql` | `whatsapp_conexao_status`, `whatsapp_conexao_transicao` (`IF NOT EXISTS`) |

Os `ensure*` correspondentes a V2 (webhook dedup, pgvector/memória, feedback) foram **removidos** do `SchemaAutoPatchService`.

### Autopatch só (legado, ainda sem migration dedicada além do que já está no V1)

Patches `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` para objectos que o V1 já descreve (contas, agendamentos, metas, família, notificações, `movimentacao_saldo_log`, `usuario_sessoes_contexto`, colunas de utilizador, etc.). Em base **já migrada por Flyway** são no-op.

Também: `jarvis_cronos_evento_log` (não está no V1).

### Comportamento esperado em produção (não executar daqui)

1. **VPS com `flyway_schema_history` alinhado** — Flyway só aplica versões em falta. Autopatch legado é `IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`. Tabelas WhatsApp / audit / V2 não são recriadas pelo autopatch.
2. **VPS antiga sem histórico Flyway** — `baseline-on-migrate=true` + `baseline-version=0` faz o Flyway **tentar V1** (`CREATE TABLE` sem `IF NOT EXISTS`) → **falha** se as tabelas já existirem. Não é este PR a corrigir; precisa de baseline real (`pg_dump` + `baseline-version=1`) ou histórico já preenchido. Ver backlog abaixo.
3. **Banco novo (CI / local limpo)** — Flyway aplica V1…Vn; app sobe; autopatch no-op no que o V1 já criou.

H2 de teste: Flyway off; entidades JPA (`ddl-auto=create`) cobrem `whatsapp_conexao_*`.

## Convergência (backlog)

1. Backup + restore em descartável antes de endurecer VPS.
2. Baseline real de produção **ou** `flyway_schema_history` já correcto — não assumir que V1 é o schema da VPS.
3. Ir desligando `ensure*` do autopatch **depois** de cada objecto ter migration estável (V1 overlap é o grosso que resta).
4. `validate-on-migrate=true` só com histórico limpo nas VPS.
5. `edith_tool_audit` poderia ganhar `IF NOT EXISTS` só se alguma VPS criou a tabela fora do Flyway — hoje o versionamento rígido é o correcto.

**Não** deixar o Flyway inerte. **Não** voltar a duplicar DDL no autopatch para objectos que já têm migration própria.
