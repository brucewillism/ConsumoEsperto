# Índice da documentação — ConsumoEsperto

Mapa de todos os ficheiros de referência do repositório.

## Visão de produto e arquitetura

| Ficheiro | Descrição |
|----------|-----------|
| [`README.md`](../README.md) | Entrada do repositório e início rápido |
| [`VISAO_GERAL.md`](VISAO_GERAL.md) | O que o sistema faz: telas, APIs, JARVIS, deploy |
| [`FUNCIONALIDADES.md`](FUNCIONALIDADES.md) | **Guia completo** de módulos e como cada um funciona |
| [`CALCULOS_FINANCEIROS.md`](CALCULOS_FINANCEIROS.md) | Patrimônio, projeções, consignado, faturas, provisões |
| [`AVISO_MUDANCAS_CALCULOS.md`](AVISO_MUDANCAS_CALCULOS.md) | Texto para o utilizador antes de deploy com mudanças numéricas |
| [`POLITICA_STATUS_TRANSACAO.md`](POLITICA_STATUS_TRANSACAO.md) | CONFIRMADA vs PREVISTO (T1) |
| [`POLITICA_PROVISAO.md`](POLITICA_PROVISAO.md) | Dedup percentual e precedência de provisões |
| [`POLITICA_STATUS_FATURA_PREVISTA.md`](POLITICA_STATUS_FATURA_PREVISTA.md) | Faturas PREVISTA e sync de totais |
| [`PLANO_CONVERSAO_COMPRA_PARCELADA.md`](PLANO_CONVERSAO_COMPRA_PARCELADA.md) | Convergência CompraParcelada → fluxo principal |
| [`BACKLOG_FISCAL_ESTIMATIVA.md`](BACKLOG_FISCAL_ESTIMATIVA.md) | Fiscal simplificado — evolução futura |
| [`MODULO_FAMILIA.md`](MODULO_FAMILIA.md) | Grupo familiar, convites, orçamentos partilhados, racha-contas |
| [`FRONTEND_OVERLAY_MODAIS.md`](FRONTEND_OVERLAY_MODAIS.md) | Modais Angular Material: overlay CDK, scroll, z-index |
| [`WHATSAPP_EVOLUTION.md`](WHATSAPP_EVOLUTION.md) | Evolution API: QR, webhook, privacidade, sessão |
| [`JARVIS_PROTOCOLOS.md`](JARVIS_PROTOCOLOS.md) | Advisor, empréstimo consignado, Sentinela, fiscal, áudio, cron jobs |
| [`INTEGRIDADE_SALDO.md`](INTEGRIDADE_SALDO.md) | Lock de saldo, audit trail, reconciliação, reparo financeiro, alertas, débito automático de fixas |
| [`WEBHOOK_AUTH_EVOLUTION.md`](WEBHOOK_AUTH_EVOLUTION.md) | Autenticação do webhook Evolution: secret, rollback, testes |

## Ambiente e deploy

| Ficheiro | Descrição |
|----------|-----------|
| [`CONFIGURACAO_AMBIENTE.md`](../CONFIGURACAO_AMBIENTE.md) | Desenvolvimento local Windows |
| [`docker/README.md`](../docker/README.md) | Docker Compose produção (portas, Flyway, Ollama) |
| [`docker/REVERSO_PROXY_502.md`](../docker/REVERSO_PROXY_502.md) | Erro 502 atrás de proxy reverso |
| [`.env.example`](../.env.example) | Variáveis Spring + Evolution (dev) |
| [`docker/env.docker.example`](../docker/env.docker.example) | Variáveis Docker Compose (VPS) |

## Scripts e ferramentas

| Caminho | Descrição |
|---------|-----------|
| [`scripts/subir-stack.ps1`](../scripts/subir-stack.ps1) | Sobe Evolution + backend + frontend (janelas CMD) |
| [`scripts/parar-servicos.ps1`](../scripts/parar-servicos.ps1) | Encerra portas 14200, 18080, 18081 e processos do repo |
| [`scripts/run-backend-dev-evolution.ps1`](../scripts/run-backend-dev-evolution.ps1) | Spring Boot perfil `dev-evolution`, porta 18081 |
| [`scripts/mvn-backend.ps1`](../scripts/mvn-backend.ps1) | `mvn` com JDK/Maven de `tools/` |
| [`scripts/stack-ports.ps1`](../scripts/stack-ports.ps1) | Constantes de portas da stack local |
| [`scripts/sincronizar-evolution-env.ps1`](../scripts/sincronizar-evolution-env.ps1) | Alinha `.env` da Evolution com o backend |
| [`test-webhook-local.http`](../test-webhook-local.http) | Pedidos HTTP de teste ao webhook Evolution |

## Código-fonte (referência rápida)

| Área | Caminho |
|------|---------|
| Rotas Angular | `frontend/src/app/app.routes.ts` |
| Catálogo app ↔ WhatsApp | `backend/.../WhatsAppAppParityService.java` |
| API família | `backend/.../GrupoFamiliarController.java` |
| Webhook WhatsApp | `backend/.../EvolutionWebhookController.java` (ou equivalente em `/api/public/evolution/webhook`) |
| Schema BD (patches runtime) | `backend/.../SchemaAutoPatchService.java` |
| Catálogo app ↔ WhatsApp | `backend/.../WhatsAppAppParityService.java` |
| Empréstimo consignado | `backend/.../EmprestimoService.java` |
| Sentinela / patrimônio | `backend/.../PrevisaoFluxoCaixaService.java`, `SaldoService.java` |
| Movimentação de saldo (ponto único, lock + audit) | `backend/.../SaldoMovimentacaoService.java` |
| Reparo financeiro (dry-run/aplicar) | `backend/.../ReparoFinanceiroController.java`, `SaldoReparoService.java` |
| Alertas operacionais | `backend/.../AlertaOperacionalService.java` |
| Débito automático de despesas fixas | `backend/.../DespesaFixaDebitoAutomaticoService.java` |

## Agentes Cursor

| Ficheiro | Descrição |
|----------|-----------|
| [`.cursor/rules/stack-local.mdc`](../.cursor/rules/stack-local.mdc) | Como subir a stack local para desenvolvimento |

## Manutenção

Ao alterar funcionalidades visíveis ao utilizador:

1. Atualizar `WhatsAppAppParityService.CATALOGO` (fonte da verdade do catálogo).
2. Atualizar [`FUNCIONALIDADES.md`](FUNCIONALIDADES.md), [`JARVIS_PROTOCOLOS.md`](JARVIS_PROTOCOLOS.md) e secções relevantes em `VISAO_GERAL.md`.
3. Se mudarem **cálculos visíveis** → [`CALCULOS_FINANCEIROS.md`](CALCULOS_FINANCEIROS.md) e [`AVISO_MUDANCAS_CALCULOS.md`](AVISO_MUDANCAS_CALCULOS.md).
4. Se mudarem portas ou serviços Docker → `docker/README.md` e `stack-ports.ps1`.
5. Schema BD → `SchemaAutoPatchService.java` (não há Flyway SQL neste repo).

**Última revisão:** julho/2026
