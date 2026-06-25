# Índice da documentação — ConsumoEsperto

Mapa de todos os ficheiros de referência do repositório.

## Visão de produto e arquitetura

| Ficheiro | Descrição |
|----------|-----------|
| [`README.md`](../README.md) | Entrada do repositório e início rápido |
| [`VISAO_GERAL.md`](VISAO_GERAL.md) | O que o sistema faz: telas, APIs, JARVIS, deploy |
| [`MODULO_FAMILIA.md`](MODULO_FAMILIA.md) | Grupo familiar, convites, partilha de orçamentos, racha-contas |
| [`FRONTEND_OVERLAY_MODAIS.md`](FRONTEND_OVERLAY_MODAIS.md) | Modais Angular Material: overlay CDK, scroll, z-index |
| [`WHATSAPP_EVOLUTION.md`](WHATSAPP_EVOLUTION.md) | Evolution API: QR, webhook, privacidade, sessão |
| [`JARVIS_PROTOCOLOS.md`](JARVIS_PROTOCOLOS.md) | Advisor, empréstimo consignado, Sentinela, fiscal, áudio, cron jobs |

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
| Sentinela / disponibilidade | `backend/.../PrevisaoFluxoCaixaService.java`, `SentinelaProtocolService.java` |

## Agentes Cursor

| Ficheiro | Descrição |
|----------|-----------|
| [`.cursor/rules/stack-local.mdc`](../.cursor/rules/stack-local.mdc) | Como subir a stack local para desenvolvimento |

## Manutenção

Ao alterar funcionalidades visíveis ao utilizador:

1. Atualizar `WhatsAppAppParityService.CATALOGO` (fonte da verdade do catálogo).
2. Atualizar [`JARVIS_PROTOCOLOS.md`](JARVIS_PROTOCOLOS.md) e secções relevantes em `VISAO_GERAL.md`.
3. Se mudarem portas ou serviços Docker → `docker/README.md` e `stack-ports.ps1`.
4. Schema BD → `SchemaAutoPatchService.java` (não há Flyway SQL neste repo).

**Última revisão:** junho/2026
