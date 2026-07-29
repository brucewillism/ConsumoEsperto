# Procedimento seguro de rotação de credenciais

> **Não executar em produção sem autorização explícita.** Este documento não contém secrets.

## Classificação atual

| Item | Status |
| --- | --- |
| Arquivos atuais sanitizados | `[x] VALIDADO` |
| Histórico Git comprometido | `[G] LEGADO` |
| Rotação PostgreSQL | `[-] PARCIAL — NÃO EXECUTADA` |
| Rotação JWT | `[-] PARCIAL — NÃO EXECUTADA` |
| Invalidação credenciais antigas | `[ ] AUSENTE` |

## 1. PostgreSQL

1. Criar novo usuário/senha no PostgreSQL (privilégios mínimos no banco da aplicação).
2. Atualizar secret no ambiente (GitHub Secrets / vault / `.env` local **não versionado**).
3. Reiniciar backend com novas variáveis `DATABASE_USERNAME` / `DATABASE_PASSWORD`.
4. Validar `GET /actuator/health` e login de usuário de teste.
5. Invalidar/revogar senha do usuário antigo no PostgreSQL.
6. Confirmar que conexão com credencial antiga falha (`psql` ou tentativa de start do backend).

## 2. JWT_SECRET

1. Gerar nova chave (≥ 256 bits, aleatória).
2. Atualizar secret no ambiente (`JWT_SECRET`).
3. Reiniciar backend.
4. Validar login emite token novo.
5. Confirmar que tokens emitidos com secret antigo retornam 401.
6. Encerrar sessões antigas conforme política (stateless: tokens expiram naturalmente em até 24 h).

## 3. Integrações e CI

1. Atualizar secrets no GitHub Actions (staging/produção separados).
2. Atualizar Evolution/WhatsApp webhooks se usarem credenciais compartilhadas.
3. Reexecutar pipeline de integração após rotação em staging.

## 4. Limpeza do histórico Git (procedimento separado)

A rotação **não** remove secrets do histórico.

1. Backup completo do repositório.
2. Branch protegida congelada.
3. Executar `git filter-repo` ou BFG (fora do fluxo automático).
4. Novo clone de validação + `gitleaks detect`.
5. Comunicar colaboradores para reclonar.
6. Invalidar forks/clones antigos.

## Checklist operacional

```
[ ] Novo usuário PostgreSQL criado
[ ] Secrets de produção atualizados (vault/GitHub)
[ ] Backend reiniciado
[ ] Health e login validados
[ ] Senha PostgreSQL antiga invalidada
[ ] Novo JWT_SECRET gerado e aplicado
[ ] Token antigo rejeitado (401)
[ ] Integrações verificadas
[ ] CI/staging com secrets exclusivos
[ ] gitleaks limpo no HEAD (histórico tratado separadamente)
```
