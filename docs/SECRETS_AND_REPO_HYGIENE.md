# Higiene de segredos e repositório (proposta — não executar limpeza de histórico automaticamente)

## Ações seguras já recomendadas

1. Remover dumps do índice git: `evolution.dump`, `sistema_java.dump`
2. Adicionar `*.dump` ao `.gitignore`
3. Unificar exemplos de env: usar **`.env.example`** como fonte única; `env.example` legado aponta para ele

## Varredura de histórico git (PROPOSTA — executar manualmente)

```bash
# Buscar padrões suspeitos no histórico
git log --all -p | rg -i "(api[_-]?key|jwt_secret|password|sk-[a-z0-9]{20,}|AKIA[0-9A-Z]{16})" | head -100

# Se encontrar segredo real commitado:
# 1. Rotacionar imediatamente a chave no provedor (OpenAI, Evolution, JWT, etc.)
# 2. Limpar histórico (exemplo com git-filter-repo):
#    pip install git-filter-repo
#    git filter-repo --path .env --invert-paths
#    git push --force-with-lease  # coordenar com equipe
```

## Achados conhecidos neste repositório

| Item | Risco | Recomendação |
|------|-------|--------------|
| `evolution.dump` / `sistema_java.dump` no git | Médio — podem conter dados/estrutura sensível | Remover do índice; manter off-site |
| `env.example` duplicado de `.env.example` | Baixo — confusão operacional | Unificar |
| Chaves em `application-secure.properties` (placeholders `${...}`) | Baixo se só placeholders | Nunca commitar valores reais |

## Logs

- Mascaramento global via `logback-spring.xml` + `LogSanitizingConverter` (apikey, JWT, Bearer, etc.).
- Revisar periodicamente: `rg "apikey|Bearer eyJ|sk-" backend/src --glob "*.java"`

## Rotação de segredos (PROPOSTA — executar manualmente)

Se `evolution.dump` / `sistema_java.dump` ou commits antigos expuseram dados reais:

| Segredo | Ação |
|---------|------|
| `EVOLUTION_API_KEY` | Regenerar em `AUTHENTICATION_API_KEY` na Evolution; atualizar `.env` e reiniciar |
| `EVOLUTION_WEBHOOK_SECRET` | Gerar token novo; atualizar `WEBHOOK_GLOBAL_URL` e `evolution.webhook.secret` |
| `JWT_SECRET` | Rotacionar força logout de todos; comunicar utilizadores |
| Chaves IA (OpenAI, Groq, Gemini…) | Revogar no painel do provedor e emitir novas |
| Password DB | `ALTER USER` no Postgres + atualizar `DATABASE_PASSWORD` |

**Ordem recomendada:** rotacionar chaves nos provedores → atualizar `.env` → reiniciar serviços → só então considerar limpeza de histórico git.

**Não reescrever histórico git sem rotação de chaves e acordo da equipe.**
