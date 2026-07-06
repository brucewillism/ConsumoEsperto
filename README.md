# ConsumoEsperto

Aplicação de finanças pessoais com **app web** (Angular), assistente **J.A.R.V.I.S.** no **WhatsApp** (Evolution API) e automações proativas (PDF de fatura, OCR, protocolos de contenção, Modo Viagem).

## Documentação

| Documento | Conteúdo |
|-----------|----------|
| [`docs/VISAO_GERAL.md`](docs/VISAO_GERAL.md) | Arquitetura, telas, APIs, JARVIS, deploy |
| [`docs/FUNCIONALIDADES.md`](docs/FUNCIONALIDADES.md) | **Todas as funcionalidades e como funcionam** |
| [`docs/CALCULOS_FINANCEIROS.md`](docs/CALCULOS_FINANCEIROS.md) | Fórmulas: patrimônio, projeções, consignado, provisões |
| [`docs/AVISO_MUDANCAS_CALCULOS.md`](docs/AVISO_MUDANCAS_CALCULOS.md) | Aviso ao utilizador antes de deploy |
| [`docs/INDICE.md`](docs/INDICE.md) | Índice completo da documentação |
| [`CONFIGURACAO_AMBIENTE.md`](CONFIGURACAO_AMBIENTE.md) | Setup local Windows (Evolution Node, Spring, Angular) |
| [`docker/README.md`](docker/README.md) | Docker Compose na VPS (produção) |
| [`docs/MODULO_FAMILIA.md`](docs/MODULO_FAMILIA.md) | Grupo familiar, convites, orçamentos partilhados, racha-contas |
| [`docs/FRONTEND_OVERLAY_MODAIS.md`](docs/FRONTEND_OVERLAY_MODAIS.md) | Modais Material/CDK, scroll e cliques |
| [`docs/WHATSAPP_EVOLUTION.md`](docs/WHATSAPP_EVOLUTION.md) | Pareamento QR, privacidade, sessão estável |
| [`docs/JARVIS_PROTOCOLOS.md`](docs/JARVIS_PROTOCOLOS.md) | Advisor, consignado, Sentinela, fiscal, áudio, jobs |
| [`docs/INTEGRIDADE_SALDO.md`](docs/INTEGRIDADE_SALDO.md) | Lock de saldo, audit trail, reparo financeiro, alertas, débito automático de fixas |
| [`.env.example`](.env.example) | Variáveis de ambiente comentadas |
| [`.cursor/rules/stack-local.mdc`](.cursor/rules/stack-local.mdc) | Regra para agentes: stack local |

## Início rápido

### Produção (VPS)

```bash
cp docker/env.docker.example .env
nano .env
git pull
docker compose up -d --build
```

App: `http://SEU_SERVIDOR:8181` · API: `8087` · Evolution: `8585`

### Desenvolvimento local (Windows)

Portas (`scripts/stack-ports.ps1`): Evolution **18080**, Spring **18081**, Angular **14200**.

```powershell
Copy-Item .env.example .env
# Configure Postgres, EVOLUTION_API_KEY, JWT_SECRET, etc.

# Parar processos anteriores
.\scripts\parar-servicos.ps1

# Evolution (Node) — em tools\evolution-api após clone + npm install/build
cd tools\evolution-api
npm run start:prod

# Backend (outro terminal)
.\scripts\run-backend-dev-evolution.ps1

# Frontend (outro terminal)
cd frontend
npm start
```

Alternativa: `.\scripts\subir-stack.ps1` abre três janelas CMD (requer wrappers `.bat` na raiz do projeto, se existirem na sua cópia).

## Stack

| Camada | Tecnologia |
|--------|------------|
| Frontend | Angular 19, Material, Chart.js |
| Backend | Spring Boot 3, Java 17, Flyway, JWT |
| WhatsApp | Evolution API (`evoapicloud/evolution-api`) |
| IA | Groq → OpenAI → Claude → Gemini → DeepSeek → Ollama |
| Dados | PostgreSQL (+ pgvector) · Redis (Evolution) |

## Repositório

```
backend/          Spring Boot
frontend/         Angular
docker/           Exemplos .env Docker, troubleshooting proxy
docs/             Visão geral e guias temáticos
scripts/          PowerShell: stack, backend, parar serviços
tools/            JDK, Maven, Node (opcional), evolution-api (clone local)
```

**Última revisão da documentação:** julho/2026
