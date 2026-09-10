# Fase 0 — Diagnóstico: WhatsApp cai após ~4 dias (Evolution API)

**Data:** 2026-09-09  
**Sintoma:** sessão funciona ~4 dias, deixa de receber/enviar em silêncio; reconectar manualmente resolve; o ciclo repetiu.  
**Stack real deste repo:** Spring Boot **2.7.18** (não 3), Java 17, Angular 19, Evolution em Docker.

Postura: evidência no repositório/compose **antes** de código. Nada aqui actualiza a imagem da Evolution.

---

## Tabela de hipóteses

| # | Hipótese | Evidência | Veredito | Recomendação |
|---|----------|-----------|----------|--------------|
| 1 | Persistência da sessão (volume / Redis eviction) | `docker-compose.yml`: volume `evolution_data:/evolution/instances`; volumes nomeados `postgres_data`, `redis_data`, `evolution_data`. Redis `redis:alpine` monta `/data`, **sem** `maxmemory` / `maxmemory-policy` (default Redis = `noeviction`). `DEL_INSTANCE` **não** está definido. `CACHE_REDIS_ENABLED=true` **e** `CACHE_LOCAL_ENABLED=true` (cache duplo). `docker compose down` **sem** `-v` preserva volumes. | **Parcialmente descartada** como causa única (a sessão *não* some só porque o compose não tem volume). **Inconclusiva** para dessincronia Redis vs disco após restart do contentor. | Manter volumes. **Backup antes** de recriar contentores. Ligar AOF no Redis (`appendonly yes`) e `maxmemory-policy noeviction` explícito. Não usar `docker compose down -v`. Considerar `CACHE_LOCAL_ENABLED=false` só depois de confirmar que o Redis está estável (não mudar às cegas em produção). **Nunca** `DEL_INSTANCE=true`. |
| 2 | Tag da imagem / Baileys | Imagem **`evoapicloud/evolution-api:latest`** (tag móvel). Docs internas (`docs/WHATSAPP_EVOLUTION.md`, `docker/README.md`) já alertam `latest` e mencionam falhas conhecidas em v2.2.x (`GET /instance/connect` → `{count:0}`) e recomendação comunitária ≥ v2.3.7. | **Provável factor agravante.** `latest` muda sob os pés; versões Baileys instáveis são causa clássica de queda silenciosa. | **Não actualizar neste PR.** Pin recomendado (humano, com backup de volumes): tag estável ≥ v2.3.7 (ex. `evoapicloud/evolution-api:v2.3.7`). Re-pareamento pode ser necessário após o pin. |
| 3 | Config de sessão / keepalive / TTL | `QRCODE_LIMIT` default **90** (só validade do QR, não da sessão). `CONFIG_SESSION_PHONE_VERSION` default **`2.3000.1033893291`** — versão antiga do WhatsApp Web; o WhatsApp invalida companions desactualizados após dias. Watchdog já existe: poll 5 min, keepalive **4 h**. Recover = `GET /instance/connect/{name}` e, se falhar, `restart` — **não apaga** a instância nesse caminho. Se o pareamento morreu no servidor WA, o recover falha com `log.warn`/`debug` e **ninguém é avisado**. | **Provável (PHONE_VERSION pinada + falha quieta).** TTL/DEL_INSTANCE **não** explicam o ciclo. Keepalive de 4 h é frouxo face ao ping típico do WhatsApp Web. | Deixar `CONFIG_SESSION_PHONE_VERSION` **vazio** (auto) ou copiar a versão do menu do WhatsApp Web do telemóvel. Explicitar `DEL_INSTANCE=false`. Não encurtar keepalive de forma agressiva (restart em loop piora Baileys). |
| 4 | Webhook `CONNECTION_UPDATE` ignorado | Compose: `WEBHOOK_EVENTS_CONNECTION_UPDATE=true`. Backend já trata `connection.update` / `CONNECTION_UPDATE` / `connection-update` em `EvolutionWebhookController.handleConnectionUpdate` → `onConnectionLost` → recover. Métricas de sessão são **só em memória** (`EvolutionSessionMetricsService`). | **Descartada** como “o backend ignora o evento”. **Provável** que o evento **não chegue** em logout silencioso do WA, ou o recover falhe e o estado se perca no restart do backend. | Reagir ao evento **e** persistir transições; polling continua como rede de segurança. Alerta só quando o recover esgota. |
| 5 | Restart de contentor / OOM / cron de deploy | Todos os serviços: `restart: always`. **Sem** `mem_limit` / `deploy.resources`. Nenhum cron de deploy no repo com ciclo de 4 dias. | **Inconclusiva** sem logs da VPS. OOM/restart isolado não explica tão bem o **ciclo regular de ~4 dias** quanto invalidação WA Web / PHONE_VERSION. | Inspecionar `docker inspect` / `dmesg` / cron da VPS. Opcional: limites de memória *depois* de medir RSS da Evolution. |

---

## Causa raiz mais plausível (síntese)

Combinação, não um único bug de volume:

1. O WhatsApp/Baileys **invalida o companion** após alguns dias, agravado por **`CONFIG_SESSION_PHONE_VERSION` pinada e antiga** e imagem **`latest`**.
2. O watchdog **já tenta** `connect`/`restart`, mas quando as credenciais no servidor WA morreram isso **falha em silêncio** (log, sem e-mail, sem histórico persistido).
3. O utilizador só descobre ao tentar usar o bot.

Persistência em disco **já existe**. Recriar a instância automaticamente **não** é a correcção (perde o pareamento).

---

## O que já existe e não deve ser duplicado

| Peça | Onde |
|------|------|
| Watchdog + keepalive | `EvolutionSessionWatchdogService` |
| `CONNECTION_UPDATE` | `EvolutionWebhookController` (~`handleConnectionUpdate`) |
| Reconnect sem delete | `EvolutionPairingService.attemptSessionReconnect` → `GET /instance/connect/{name}` |
| Restart | `EvolutionInstanceSettingsService.restartInstance` |
| Estado | `GET /instance/connectionState/{instance}` |
| Pairing por número na REST | `GET /instance/connect/{instance}?number=` (já parseia `pairingCode`) |
| Alertas | `AlertaOperacionalService` (log ERROR + webhook opcional, cooldown in-memory). **Sem SMTP** até este trabalho. |
| Filtro / dedup | `EvolutionWebhookApiKeyFilter` + `EvolutionWebhookDedupService` — não alterar o contrato. |

---

## Recomendações de compose/env (humano na VPS)

**Backup dos volumes** (`postgres_data`, `redis_data`, `evolution_data`) **antes** de recriar contentores.

1. Pin da imagem (quando o humano autorizar): `evoapicloud/evolution-api:v2.3.7` (ou a tag estável actual) — **não** feito neste PR.
2. `CONFIG_SESSION_PHONE_VERSION` vazio ou igual à versão no fundo do WhatsApp Web.
3. Redis: `--appendonly yes --maxmemory-policy noeviction`.
4. `DEL_INSTANCE=false` explícito.
5. **Não** ligar limpeza/TTL que apague instância.
6. SMTP no `.env`: `MAIL_*` + `ALERTA_EMAIL_DESTINO` (Gmail: **senha de app**, não a senha da conta).

Ajustes **seguros** aplicados no `docker-compose.yml` deste PR: AOF Redis, `DEL_INSTANCE=false`, default vazio de `PHONE_VERSION`, variáveis de mail/monitor no backend. A tag da imagem permanece `latest` até decisão humana.
