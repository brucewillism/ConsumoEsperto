# ConsumoEsperto — matriz de ambientes e comandos executáveis

## Matriz de ambientes

| Recurso | Local sem Docker | Local com Docker | GitHub Actions | Staging |
| ------- | ---------------- | ---------------- | -------------- | ------- |
| JDK 17 | `tools/java/ms-17.0.15` | idem | `setup-java@v4` temurin 17 | instalado no host |
| Maven | `tools/maven` | idem | cache Maven | idem |
| Backend build/test | `.\scripts\mvn-backend.ps1 clean verify` | Compose + idem | `backend-ci.yml` | deploy manual |
| Frontend | `cd frontend && npm ci && npm run build` | idem | `frontend-ci.yml` | idem |
| Testes frontend | `cd frontend && npm test` | idem | idem | idem |
| PostgreSQL | serviço Windows local | container Compose | service container CI | VPS |
| Smoke integração HTTP | `.\scripts\smoke-integracao.ps1` | stack completa | `integracao-completa-ci.yml` | manual |
| Docker stack | **LIMITADO PELO AMBIENTE** se Docker ausente | `docker compose up` | opcional | VPS |
| Fiscal | JSON em `backend/src/main/resources/fiscal/tabelas/` | idem | testes unitários | idem |
| Migração CompraParcelada | dry-run via serviço (não produção) | banco teste | não automático | documentado |

## Comandos

```powershell
# Backend
.\scripts\mvn-backend.ps1 clean test
.\scripts\mvn-backend.ps1 verify

# Frontend
cd frontend
npm ci
npm test
npm run build

# Integração completa (backend + smoke HTTP)
.\scripts\run-integracao-completa.ps1

# PostgreSQL smoke (não destrutivo em produção)
.\scripts\postgres-smoke.ps1
```

## Variáveis (ver `.env.example`)

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` — nunca commitar valores reais
- `JWT_SECRET`, `EVOLUTION_API_KEY` — secrets locais/staging

## JDK / Maven locais

Documentados em `tools/README.md`. O script `scripts/mvn-backend.ps1` configura `JAVA_HOME` e PATH automaticamente.
