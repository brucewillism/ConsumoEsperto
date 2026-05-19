# Docker — ConsumoEsperto

Ficheiro principal: **`docker-compose.yml`** na raiz do repositório.

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

## Bases de dados

- **`consumo_db`** (ou o valor de `POSTGRES_DB`) — Spring / Flyway.  
- **`evolution_api`** — criada pelo script em `docker/init-db/`; **não** uses a mesma BD do app para a Evolution.

## Ollama

URL **interna** para o backend: `http://ollama:11434`. No host: porta **11999**.

## Falha ao login Google: `relation "usuarios" does not exist`

Nos logs, o utilizador existe na Google mas o Hibernate falha: **sem tabela `usuarios`** e o Flyway indica *“No migration necessary”* — típico de **JAR sem scripts** em `classpath:db/migration` na VPS ou **build Docker com cache**.

**Correcção rápida (sem rebuild):**

```bash
cat docker/bootstrap-usuarios.sql | docker exec -i consumo_postgres psql -U consumo -d consumo_db
```

(Ajuste `consumo` e `consumo_db` ao seu `.env`.)

**Correcção estrutural:** confirme `backend/src/main/resources/db/migration/V1__create_usuario_table.sql` na VPS (`ls`, `git pull`). Depois:

```bash
docker compose build --no-cache backend && docker compose up -d backend
```

Verificar migrações no JAR:

```bash
docker run --rm --entrypoint sh consumoesperto-backend -c \
  "unzip -l /app/app.jar | grep db/migration"
```

## Imagem Evolution

Por defeito: `atendare/evolution-api:latest`. Se falhar, defina `EVOLUTION_IMAGE` no `.env` (ex. `atendai/evolution-api:v2.1.1` ou `evoapicloud/evolution-api:latest`).

Se a imagem **só escutar na 8080**, no `docker-compose.yml` mude para `8585:8080`, `SERVER_PORT=8080` e `EVOLUTION_URL` / `SERVER_URL` coerentes.

## Redis

Incluído para a Evolution v2. Para desativar, remova o serviço `redis`, `depends_on` e em `evolution_api` ponha `CACHE_REDIS_ENABLED=false` (pode ser instável consoante a imagem).
