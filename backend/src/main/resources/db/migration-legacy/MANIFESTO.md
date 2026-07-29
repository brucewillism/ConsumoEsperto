# Manifesto — migrations Flyway legadas

## Motivo do arquivamento

Entre abril e maio de 2026 existiam **14 scripts SQL** em `db/migration/` com **versões conflitantes** (duas `V1`, uma `V2` duplicada conceptualmente). Isso impedia o bootstrap de banco vazio (`FlywayException: Found more than one migration with version 1`).

As migrations foram **substituídas** por:

| Arquivo ativo | SHA-256 | Função |
| ------------- | ------- | ------ |
| `../migration/V1__baseline_inicial.sql` | `11FA882FFA4717624A93EDF23B339B474716EE184CBE65462DAC084F7711DC9D` | Schema completo das 36 entidades JPA + constraints/indexes Hibernate |
| `../migration/V2__schema_autopatch_complementar.sql` | `4126772ECB026D31F2C171950E24C765699567BD98B91574CE1D2C0E08011E12` | Tabelas não-JPA (memória semântica, webhook dedup, migração compra parcelada) |

**Este diretório não está no classpath Flyway.** Locations ativas: `classpath:db/migration` apenas.

---

## Inventário das 14 migrations arquivadas

| Migration antiga | SHA-256 | Conteúdo | Objeto afetado | Incluída na V1/V2? | Ainda necessária? |
| ---------------- | ------- | -------- | -------------- | ------------------ | ----------------- |
| `V1__create_usuario_table.sql` | `C0E2974205670391…` | CREATE TABLE usuarios (subset colunas) | `usuarios` | **Sim** — V1 baseline (36 tabelas, UK email/username/google_id/whatsapp) | **Não** para banco vazio |
| `V1__add_whatsapp_numero_to_usuarios.sql` | `579AB0552C8F50D9…` | ADD `whatsapp_numero` + índice | `usuarios` | **Sim** — V1 usa `whatsapp_number` (nome JPA correto) | **Não** |
| `V202605191000__create_usuarios.sql` | `F6CC7D7E42EEA55C…` | Duplicata CREATE usuarios | `usuarios` | **Sim** — V1 | **Não** |
| `V2__whatsapp_cleanup_and_legacy_drop.sql` | `34A53D8A7CF48070…` | RENAME whatsapp_numero→whatsapp_number; DROP tabelas legadas | `usuarios`, legado | **Parcial** — coluna OK em V1; DROP legado **não** reaplicar em prod sem análise | **Só em bancos legados** que ainda tenham tabelas órfãs |
| `V3__add_recorrencia_and_soft_delete_transacoes.sql` | `EE06F3813BF0A635…` | recorrente, frequencia, excluido, índices | `transacoes` | **Sim** — colunas na entidade/V1 | **Não** |
| `V4__add_status_conferencia_to_transacoes.sql` | `5B29DFB7FA5722BF…` | status_conferencia + índice | `transacoes` | **Sim** — V1 | **Não** |
| `V5__add_cnpj_to_transacoes.sql` | `5F4773CB13FF03BC…` | cnpj + índice | `transacoes` | **Sim** — V1 | **Não** |
| `V20260130120000__transacao_fatura_id.sql` | `0DB055F9811015CA…` | fatura_id FK | `transacoes` | **Sim** — V1 | **Não** |
| `V20260502140000__parcelas_inteligentes.sql` | `3F519AAEE6AF5E6F…` | grupo_parcela_id, parcelas, valores | `transacoes` | **Sim** — V1 | **Não** |
| `V6__whatsapp_lembrete_pendencia.sql` | `C1D6CC86CB5C3392…` | CREATE whatsapp_lembrete_pendencia | tabela + FK | **Sim** — V1 | **Não** |
| `V7__create_metas_financeiras.sql` | `2040BA3ABDB7DF86…` | CREATE metas_financeiras | tabela | **Sim** — V1 (entidade evoluiu; colunas extras via JPA) | **Não** |
| `V8__create_usuario_ai_config.sql` | `8EB4A7E696118B57…` | CREATE usuario_ai_config | tabela | **Sim** — V1 | **Não** |
| `V9__create_usuario_renda_config.sql` | `AEBC755D6B21C2D4…` | CREATE usuario_renda_config | tabela | **Sim** — V1 | **Não** |
| `V20260201103000__notificacoes_fechamento_cartao.sql` | `F003E2949EB7064F…` | CREATE notificacoes_fechamento_cartao | tabela | **Sim** — V1 | **Não** |

Nenhuma migration legada continha **dados seed** obrigatórios, **triggers**, **functions** ou **views**. Extensão **pgvector** é opcional (V2 tenta CREATE EXTENSION; fallback BYTEA).

---

## Substituição por SchemaAutoPatch

Alterações que estavam só em `SchemaAutoPatchService` e foram movidas para **V2**:

- `evento_webhook_processado`
- `memoria_semantica_jarvis` (+ metadados)
- `transacao_semantica_index`
- `jarvis_feedback`
- `compra_parcelada_migracao_controle`

Configuração definitiva de runtime:

```properties
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
consumoesperto.schema.autopatch.enabled=false
```

---

## Estratégias por tipo de banco

### Banco vazio (integração, CI, novo ambiente)

```text
DROP/CREATE database → Flyway V1 → V2 → V3+ → Hibernate validate → app
```

Não usar `ddl-auto=update`. Não usar `baselineOnMigrate=true` indiscriminado.

### Banco legado **sem** `flyway_schema_history`

1. `pg_dump --schema-only` do ambiente (somente leitura).
2. Comparar com V1+V2 (tabelas, colunas, índices, FKs).
3. Corrigir diferenças via migration **incremental** (V3+) ou script manual aprovado.
4. `flyway baseline -baselineVersion=2` (ou versão acordada) **somente após** schema alinhado.
5. `flyway migrate` para versões posteriores.
6. Subir app com `ddl-auto=validate`.
7. Gerar relatório de diferenças residual.

**Não** usar `baselineOnMigrate=true` sem inventário — mascara drift.

### Banco legado **com** Flyway parcial

1. Consultar `SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank`.
2. Mapear versões legadas já aplicadas (checksums deste manifesto).
3. Se legado ≤ V9 mas schema incompleto: patch manual + `flyway repair` sob supervisão.
4. **Nunca** apagar `flyway_schema_history` em produção.
5. Novos ambientes usam apenas V1/V2 ativos.

### Risco para bancos antigos

| Risco | Mitigação |
| ----- | --------- |
| Histórico Flyway referencia scripts removidos do classpath | Manter este manifesto + checksums; usar `flyway repair` ou baseline controlada |
| Coluna `whatsapp_numero` vs `whatsapp_number` | V2 legado fazia rename; V1 já nasce com `whatsapp_number` |
| Tabelas legadas dropadas por V2 legado | Só executar DROP se confirmado que não há dados necessários |
| pgvector ausente | BYTEA fallback; busca vetorial limitada |

---

## Congelamento V1/V2

Após validação do bootstrap (registro/login/smoke):

- **Não editar** `V1__baseline_inicial.sql` nem `V2__schema_autopatch_complementar.sql` sem nova auditoria.
- Correções estruturais → `V3__descricao.sql`, `V4__…`, etc.
- Checksums definitivos registrados acima (2026-07-29).

---

## Procedimento de adoção (resumo)

```powershell
# Banco vazio local
.\scripts\integracao-postgres.ps1 -RecriarBanco
.\scripts\start-integracao.ps1
# Flyway roda no startup; validar flyway_schema_history e health
```

Para legado: consultar DBA antes de baseline/repair.
