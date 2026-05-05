# Configuracao de Ambiente - ConsumoEsperto

Guia curto para subir o projeto localmente. O fluxo padrao e:

1. Evolution API em Node na porta `8080`
2. Backend Spring Boot na porta `8081`
3. Frontend Angular na porta `4200`

## Requisitos

- JDK 17 em `tools\java\ms-17.0.15` ou outra pasta dentro de `tools\java`
- Maven em `tools\maven`
- Node.js no `PATH` ou em `tools\node`
- PostgreSQL local acessivel pelo backend e pela Evolution
- Evolution API clonada em `tools\evolution-api` ou caminho definido em `EVOLUTION_DIR`

Os scripts `.bat` configuram `JAVA_HOME` e `PATH` apenas na sessao aberta, sem alterar variaveis globais do Windows.

## Primeira Configuracao

Copie `.env.example` para `.env` e ajuste as credenciais locais:

```powershell
Copy-Item .env.example .env
```

No backend, se preferir um arquivo local de banco, crie `backend\config\db-local.properties` a partir do exemplo correspondente, quando existir. Esse caminho nao deve ser versionado com segredos reais.

## Evolution API em Node

Clone a Evolution API:

```powershell
git clone https://github.com/EvolutionAPI/evolution-api.git tools\evolution-api
```

Entre na pasta e prepare a aplicacao conforme a documentacao oficial:

```powershell
cd tools\evolution-api
npm install
npm run build
```

O script `scripts\sincronizar-evolution-env.ps1` alinha o `.env` da Evolution com:

- `SERVER_PORT=8080`
- `SERVER_URL=http://localhost:8080`
- `WEBHOOK_GLOBAL_URL=http://127.0.0.1:8081/api/public/evolution/webhook`
- `DATABASE_CONNECTION_URI` para o banco da Evolution
- `AUTHENTICATION_API_KEY` compartilhada com o backend

## Como Subir

Para subir a stack completa:

```cmd
subir-servicos.bat
```

Para subir cada parte separadamente:

```cmd
rodar-evolution.bat
rodar-backend-evolution.bat
rodar-frontend.bat
```

Para parar:

```cmd
parar-servicos.bat
```

## Compilacao do Backend

Use sempre o Java da pasta `tools`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\mvn-backend.ps1 -DskipTests compile
```

Para executar o backend com a Evolution:

```cmd
rodar-backend-evolution.bat
```

## Verificacoes Rapidas

- Evolution: `http://localhost:8080/health`
- Backend: `http://localhost:8081/actuator/health`
- Frontend: `http://localhost:4200`
- Webhook da Evolution para o Spring: `http://localhost:8081/api/public/evolution/webhook`

## Problemas Comuns

- `Nenhum JDK em tools\java`: instale/descompacte um JDK 17 dentro de `tools\java`.
- `Node.js nao encontrado`: instale Node.js ou coloque `node.exe` em `tools\node`.
- `Build nao encontrado (dist\main.js)`: rode `npm install` e `npm run build` em `tools\evolution-api`.
- `Porta 8081 ja esta em uso`: execute `parar-servicos.bat` ou encerre o processo que esta usando a porta.
- Erro de banco: confirme PostgreSQL, credenciais e se os bancos `consumoesperto` e `evolution_api` existem.
