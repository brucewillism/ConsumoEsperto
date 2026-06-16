# Docker — ConsumoEsperto

Ficheiro principal: **`docker-compose.yml`** na raiz do repositório.

Documentação relacionada: [`docs/VISAO_GERAL.md`](../docs/VISAO_GERAL.md) · [`docs/WHATSAPP_EVOLUTION.md`](../docs/WHATSAPP_EVOLUTION.md) · [`REVERSO_PROXY_502.md`](REVERSO_PROXY_502.md)

## Portas no host (VPS)

| Serviço    | Porta host |
|-----------|------------|
| PostgreSQL | **5439** |
| Backend Spring | **8087** |
| Frontend (Nginx + Angular) | **8181** |
| Evolution API | **8585** |
| Ollama | **11999** → 11434 no container |

## Arranque

```bash
cp docker/env.docker.example .env
nano .env
docker compose up -d --build
```

- **App:** `http://SEU_SERVIDOR:8181/`  
- **API direta (sem Nginx):** `http://SEU_SERVIDOR:8087/`  
- **Evolution:** `http://SEU_SERVIDOR:8585/`  

O frontend faz proxy de **`/api`** para **`http://backend:8087`** (ver `frontend/nginx.conf`).

Atualizar só o frontend após mudanças em modais/overlay:

```bash
git pull
docker compose up -d --build frontend
```

## Bases de dados

- **`consumo_db`** (ou o valor de `POSTGRES_DB`) — Spring / Flyway.  
- A Evolution no Compose usa o **mesmo** servidor Postgres (`postgres:5432`) com esquema/tabelas próprias geridas pela Evolution (não misturar credenciais de app com Manager manual na mesma instância sem cuidado).

Imagem Postgres: **`pgvector/pgvector:pg15`** (suporte a `CREATE EXTENSION vector` para memória semântica Jarvis).

## Variáveis Evolution importantes (.env)

| Variável | Uso |
|----------|-----|
| `EVOLUTION_API_KEY` | Chave partilhada backend ↔ Evolution |
| `EVOLUTION_SERVER_URL` | URL **pública** (`SERVER_URL` no contentor) — obrigatória para QR |
| `EVOLUTION_URL` | URL **interna** para o backend: `http://evolution_api:8585` |
| `EVOLUTION_CACHE_REDIS_ENABLED` | `true` (recomendado) |
| `EVOLUTION_ALWAYS_ONLINE` | `false` — preserva notificações no telemóvel |
| `EVOLUTION_PRIVACY_SET_UNAVAILABLE` | `true` — presença indisponível |

Ver comentários completos em [`docker/env.docker.example`](env.docker.example) e [`.env.example`](../.env.example).

## Ollama

URL **interna** para o backend: `http://ollama:11434`. No host: porta **11999**.

Após primeira subida:

```bash
docker exec consumo_ollama ollama pull llama3.2
```

## Falha ao login Google: `relation "usuarios" does not exist`

Nos logs, o utilizador existe na Google mas o Hibernate falha: **sem tabela `usuarios`** e o Flyway indica *“No migration necessary”* — típico de **JAR sem scripts** em `classpath:db/migration` na VPS ou **build Docker com cache**.

**Correcção estrutural:** confirme `backend/src/main/resources/db/migration/V1__create_usuario_table.sql` na VPS (`ls`, `git pull`). Depois:

```bash
docker compose build --no-cache backend && docker compose up -d backend
```

Verificar migrações no JAR:

```bash
docker run --rm --entrypoint sh consumoesperto-backend -c \
  "unzip -l /app/app.jar | grep db/migration"
```

Se precisar de SQL manual de emergência, exporte o DDL a partir das migrações Flyway em `backend/src/main/resources/db/migration/` e aplique com `psql` no contentor `consumo_postgres` (não há `docker/init-db/` neste repositório).

## Imagem Evolution

Por defeito no `docker-compose.yml`: **`evoapicloud/evolution-api:latest`**.

Para fixar outra versão, pode definir `image:` no serviço `evolution_api` ou usar override Compose local.

Se a imagem **só escutar na 8080**, no `docker-compose.yml` mude para `8585:8080`, `SERVER_PORT=8080` e `EVOLUTION_URL` / `SERVER_URL` coerentes.

## Redis

Incluído para a Evolution v2. Variáveis oficiais: **`CACHE_REDIS_ENABLED`** + **`CACHE_REDIS_URI`** (`redis://redis:6379/0`).

Para desativar, remova o serviço `redis`, `depends_on` e em `evolution_api` ponha `CACHE_REDIS_ENABLED=false` (pode ser instável consoante a imagem).

## Build em VPS com pouca RAM

Faça build de `backend` e `frontend` **separados**; `ng build` pode demorar 10–20 min:

```bash
docker compose build backend
docker compose build frontend
docker compose up -d
```
