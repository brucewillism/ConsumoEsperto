# Testes do backend (Maven + Podman/Docker)

Como correr `mvn test` completo neste repo sem falhas de infraestrutura do Testcontainers.

## O que a suíte faz

- A maior parte dos testes usa H2 em memória (`application-test.properties`).
- Testes Postgres “vanilla” (lock de saldo, unicidade de fatura/agendamento/CSV, dedup de webhook) partilham **um** contentor `postgres:16-alpine` por JVM (`SharedPostgresContainer`), cada classe com uma *database* isolada.
- `MemoriaSemanticaPostgresIntegrationTest` sobe **um** contentor extra (`pgvector/pgvector:pg16`) — precisa da extensão `vector`.
- `ToolPostgresBaselineTest` usa Postgres *embedded* (Zonky), não Docker.
- Sem motor de contentores, esses testes são *skipped* (`@EnabledIf dockerDisponivel`), não vermelhos.

Não se usa `withReuse(true)`: o Testcontainers só honra reuse se o utilizador tiver `testcontainers.reuse.enable=true` em `~/.testcontainers.properties`. O singleton in-JVM não exige isso e funciona no CI Linux.

O POM **não** força `npipe` nem `DOCKER_HOST` (partiria o CI Linux).

## Recursos da máquina Podman (Windows)

A falha `localhost:2375 failed to respond` na suíte cheia era a API do Podman a saturar: **um contentor Postgres por classe de teste** + Ryuk desligado + máquina com **2 GiB**.

| Recurso | Mínimo que já falhou | Recomendado |
|---------|----------------------|-------------|
| RAM da máquina Podman | 2 GiB | **4 GiB** |
| CPU | 2 | 4 |

```powershell
podman machine inspect --format "{{.Name}} memory={{.Resources.Memory}}"
# subir memória (máquina parada):
podman machine stop
podman machine set --memory 4096
podman machine start
```

`podman machine start` no Windows encaminha a API para `npipe:////./pipe/docker_engine`. O Testcontainers descobre isso sozinho.

Se o Ryuk falhar no Podman (privilégios):

```powershell
$env:TESTCONTAINERS_RYUK_DISABLED = "true"
```

Não definir isto no POM. No CI Linux o Ryuk deve continuar activo.

## Como correr

```powershell
# JDK/Maven do repo
$env:TESTCONTAINERS_RYUK_DISABLED = "true"   # só se o Ryuk falhar no Podman
.\scripts\mvn-backend.ps1 test
```

Repetir o mesmo comando a seguir: o resultado deve ser idêntico (contentor partilhado, sem corrida de `docker create`).

H2 isolado (já na suíte):

- `CompraParceladaMigracaoIntegracaoTest` — `jdbc:h2:mem:compra_parcela_mig` (o `mem:testdb` partilhado morria quando outro contexto saía da cache).
- `ToolSearchScaleTest` — mem própria; medição **fora** de `@Transactional` de classe (SELECT não corre dentro de uma tx com 200k inserts).

## Perfil Maven `-Pintegracao`

Não se moveu o lock de saldo para Failsafe. Esses testes **têm** de correr em `mvn test` — é o aceite do débito concorrente. Um perfil separado criaria o ponto cego que esta rodada elimina.
