# Configuracao de Ambiente - ConsumoEsperto

Guia curto para subir o projeto localmente. O fluxo padrao e:

1. Evolution API em Node na porta `18080`
2. Backend Spring Boot na porta `18081`
3. Frontend Angular na porta `14200`

(Valores em `scripts/stack-ports.ps1`; escolhidos para reduzir choque com Apache/PHP noutras portas comuns.)

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

- `SERVER_PORT=18080`
- `SERVER_URL=http://localhost:18080`
- `WEBHOOK_GLOBAL_URL=http://127.0.0.1:18081/api/public/evolution/webhook`
- `DATABASE_CONNECTION_URI` para o banco da Evolution
- `AUTHENTICATION_API_KEY` compartilhada com o backend

### Backend: URL e API key da Evolution (pareamento pelo app)

Quem faz pedidos `GET /instance/connect/...` e `GET /instance/connectionState/...` ao abrir **WhatsApp → Vincular número** e no modal do QR é o **backend Spring**. O browser do utilizador nunca recebe a chave mestra da Evolution.

- No `.env` (ou variáveis de ambiente Docker), alinhar com as mesmas credenciais que a Evolution espera na header **`apikey`**:
  - `EVOLUTION_URL` — base HTTP(S) sem barra dupla no path (mapeado para `evolution.url`).
  - `EVOLUTION_APIKEY` — igual à `AUTHENTICATION_API_KEY` da Evolution (`evolution.apikey` no Spring).
  - `EVOLUTION_INSTANCE` — nome por defeito da instância (ex.: `ConsumoEsperto`), igual ao criado no Manager/script (`evolution.instance`).

Opcionalmente, por utilizador, na configuracao de IA (`usuario_ai_config` / endpoint `GET/POST /api/config/ia`):

- `evolution_instance_name` — sobrepõe `EVOLUTION_INSTANCE` para esse utilizador ao pedir QR e estado.
- `evolution_api_key` — sobrepõe `EVOLUTION_APIKEY` se esse utilizador tiver chave dedicada na Evolution.

Fluxo no frontend **WhatsApp**:

1. Grava o número em `POST /api/usuarios/whatsapp/vincular`.
2. O backend chama a Evolution e devolve `evolutionQrCodeDataUri` e/ou `evolutionPairingCode` quando existirem na resposta.
3. Um modal faz **polling** a cada 5 s em `GET /api/usuarios/whatsapp/evolution-connection-status`; quando `connected` ficar verdadeiro, considera pareamento feito nesta **instancia** Evolution e fecha o modal.

**Nota:** Se varios utilizadores partilharem **a mesma** instancia Evolution, estado de pareamento (`connected`) e um unico numero WhatsApp Evolution sao globais dessa instancia; o numero **vinculado no perfil** do app continua a servir para identificar o tenant nas mensagens.

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

- Evolution: `http://localhost:18080/health`
- Backend: `http://localhost:18081/actuator/health`
- Frontend: `http://localhost:14200`
- Webhook da Evolution para o Spring: `http://localhost:18081/api/public/evolution/webhook`

## Problemas Comuns

- `Nenhum JDK em tools\java`: instale/descompacte um JDK 17 dentro de `tools\java`.
- `Node.js nao encontrado`: instale Node.js ou coloque `node.exe` em `tools\node`.
- `Build nao encontrado (dist\main.js)`: rode `npm install` e `npm run build` em `tools\evolution-api`.
- `Porta 18081 ja esta em uso`: execute `parar-servicos.bat` ou encerre o processo que esta usando a porta.
- Erro de banco: confirme PostgreSQL, credenciais e se os bancos `consumoesperto` e `evolution_api` existem.
