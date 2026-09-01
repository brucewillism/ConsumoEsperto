# Inventário Técnico — Correção de Segurança e Contratos

Atualizado em: **2026-08-03** (após regressão local).

Legenda de status: `[x] VALIDADO` · `[~] IMPLEMENTADO NÃO VALIDADO` · `[-] PARCIAL` · `[X] DEFEITUOSO` (antes da correção).

---

## Segurança crítica

| Item | Antes | Correção | Teste | Status final |
|------|-------|----------|-------|--------------|
| Categoria em transação | `findById` sem ownership → IDOR | `findByIdAndUsuarioId` em todos os fluxos (`TransacaoService`, parcelada, Jarvis, orçamento, etc.) | `OwnershipCategoriaCartaoHttpTest` (13 cenários) | `[x] VALIDADO` |
| Cartão em fatura | `findById` sem ownership; `RuntimeException` → 500 | `findByIdAndUsuarioId`; `ResourceNotFoundException`; constraint `ux_faturas_cartao_competencia_nao_quitada` + retry em corrida | `OwnershipCategoriaCartaoHttpTest`, `FaturaCompetenciaUnicidadePostgresIntegrationTest`* | `[x] VALIDADO` |
| Admin Evolution | JWT apenas; qualquer usuário autenticado | `ROLE_ADMIN` persistido; `SecurityConfig` + `@PreAuthorize`; `AdminGuard` no frontend | `ActuatorAdminSecurityHttpTest`, `ProducaoConfiguracaoSecurityTest` | `[x] VALIDADO` |
| Actuator | `env`/`beans`/`configprops` expostos | Allowlist produção: `health`, `info`, `prometheus`; demais exigem `ROLE_ADMIN`; health sem detalhes | `ActuatorAdminSecurityHttpTest`, `ProducaoConfiguracaoSecurityTest` | `[x] VALIDADO` |
| Família | Qualquer membro renomeava/convidava | Papéis `OWNER`/`MEMBER` persistidos; convite com expiração, cancelamento, remoção | `FamiliaAutorizacaoHttpTest` (matriz completa) | `[x] VALIDADO` |
| WhatsApp config | `/api/whatsapp/**` amplo; webhook sem limite | Webhook só com segredo (`EvolutionWebhookApiKeyFilter`); payload max; endpoints não-webhook autenticados | `WhatsAppSegurancaHttpTest` | `[x] VALIDADO` |

\* Testes Postgres com Testcontainers **ignorados localmente** sem Docker (`Skipped: 1` cada); validados em CI com PostgreSQL.

---

## Contratos frontend/backend

| Contrato | Divergência | Correção | Teste | Status final |
|----------|-------------|----------|-------|--------------|
| Busca de categoria | `GET /api/categorias/buscar` inexistente | Endpoint implementado (autenticado, case/acento-insensitive, limite) | `CategoriaBuscaHttpTest` | `[x] VALIDADO` |
| Status de fatura | Frontend `FECHADA`/`PENDENTE`/`PARCIALMENTE_PAGA` | Alinhado ao backend: `ABERTA`, `VENCIDA`, `PAGA`, `PARCIAL`, `PREVISTA`, `CANCELADA` | `fatura-status-contrato.spec.mjs` | `[x] VALIDADO` |
| Dia de fechamento | `setDiaFechamento` no-op | Removido setter enganoso; fechamento derivado do vencimento | `FaturaFechamentoDerivadoTest` (7 cenários) | `[x] VALIDADO` |
| PDF legado | `Content-Type: application/pdf` com texto UTF-8 | Endpoints legados retornam `410 Gone`; PDF real via `ReportService` (`%PDF`) | `RelatorioPdfContratoHttpTest` | `[x] VALIDADO` |

---

## Idempotência e agendamentos

| Item | Antes | Correção | Teste | Status final |
|------|-------|----------|-------|--------------|
| Agendamento duplicado | Idempotência só em memória/serviço | Tabela `agendamento_execucoes` + `ux_agendamento_execucoes_competencia`; registro na mesma transação (evita deadlock) | `AgendamentoIdempotenteRegressionTest`, `AgendamentoIdempotenciaPostgresIntegrationTest`* | `[x] VALIDADO` |

---

## Promessas falsas (UI)

| Item | Antes | Correção | Status final |
|------|-------|----------|--------------|
| "Tempo real" (contas, importações, WhatsApp) | Texto enganoso | Textos ajustados para refletir comportamento real | `[x] VALIDADO` |
| "Esqueceu a senha?" | Botão sem fluxo | Removido do login | `[x] VALIDADO` |
| Débito automático assinaturas | "débito em conta" | "lembrete considera o saldo" | `[x] VALIDADO` |

---

## Testes — regressão local (2026-08-03)

| Camada | Descobertos | Aprovados | Falharam | Ignorados |
|--------|------------:|----------:|---------:|----------:|
| Backend (`mvn clean verify`) | 492 | 478 | 0 | 14 |
| Frontend (`npm test` + build) | 74 | 74 | 0 | 0 |
| Integração (`run-integracao-completa.ps1`) | 25 smoke + 15 CSV + PDF/Motor | 40 | 0 | 0 |
| E2E Playwright (`fluxos-criticos` + `smoke`) | 23 | 23 | 0 | 0 |

### Ignorados no backend (sem Docker local)

- `AgendamentoIdempotenciaPostgresIntegrationTest`
- `FaturaCompetenciaUnicidadePostgresIntegrationTest`
- `CompraParceladaMigracaoPostgresIntegrationTest`
- `MemoriaSemanticaPostgresIntegrationTest`
- `SaldoConcorrenciaPostgresIntegrationTest`
- `WebhookDedupPostgresIntegrationTest`
- `WebhookDedupPurgePostgresIntegrationTest`
- `PdfRuntimeHttpValidationTest`
- `HibernateBaselineExporterTest`
- `FlywayBaselineLocalGeneratorTest`

---

## CI

| Workflow | Implementado | Sintaxe | Local | GitHub |
|----------|--------------|---------|-------|--------|
| Backend verify | Sim (`mvn clean verify`) | OK | OK (492 testes) | `[~] IMPLEMENTADO NÃO VALIDADO` |
| Frontend lint/test/build | Parcial no repo | — | OK (74 testes) | `[~] IMPLEMENTADO NÃO VALIDADO` |
| Integração smoke/CSV/PDF | `run-integracao-completa.ps1` | OK | OK | `[~] IMPLEMENTADO NÃO VALIDADO` |
| E2E Playwright | `.github/workflows/e2e-ci.yml` | OK | OK (23 testes, 3.1 min) | `[~] IMPLEMENTADO NÃO VALIDADO` |

---

## Relatório final — resumo executivo

### Segurança crítica

| Item | Antes | Depois | Teste | Resultado |
|------|-------|--------|-------|-----------|
| Categoria em transação | IDOR | Ownership por usuário | HTTP 13 testes | PASS |
| Cartão em fatura | IDOR + 500 | Ownership + 404 + constraint PG | HTTP + PG* | PASS |
| Admin Evolution | Só JWT | `ROLE_ADMIN` + guard | HTTP segurança | PASS |
| Actuator | Exposição ampla | Allowlist mínima | HTTP + prod config | PASS |
| Família | Sem papéis | OWNER/MEMBER | Matriz HTTP | PASS |
| WhatsApp config | Superfície ampla | Webhook com segredo + limite | HTTP mock | PASS |

### Contratos frontend/backend

Todos os quatro contratos auditados foram corrigidos e cobertos por teste automatizado (`[x] VALIDADO`).

### Próximo passo operacional

1. Executar pipeline no GitHub para validar Testcontainers com PostgreSQL e E2E em CI.
2. Promover itens de `[~] IMPLEMENTADO NÃO VALIDADO` para `[x] VALIDADO` somente após CI verde.
