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
  - `EVOLUTION_URL` — base HTTP(S) sem barra dupla no path (`evolution.url`). **No Docker Compose do repo**, o backend deve usar **`http://evolution_api:8585`** para falar diretamente com o contentor da Evolution (não usar a URL pública `/evolution` dentro da rede Docker — evita respostas HTML do proxy/WAF em vez do JSON da API).
  - `EVOLUTION_APIKEY` **ou** `EVOLUTION_API_KEY` — igual à `AUTHENTICATION_API_KEY` / `API_TOKEN` da Evolution (`evolution.apikey` no Spring lê uma ou outra; o Compose deste repo usa `EVOLUTION_API_KEY`).
  - `EVOLUTION_INSTANCE` — nome por defeito da instância (ex.: `ConsumoEsperto`), igual ao criado no Manager/script (`evolution.instance`).
  - **`EVOLUTION_SERVER_URL` (Compose → `SERVER_URL` no contentor Evolution)** — URL **pública** onde o WhatsApp / webhooks conseguem alcançar a Evolution (ex.: `https://seu-dominio:8585` se expuser a porta, ou HTTPS atrás do proxy). **É distinto do `EVOLUTION_URL`**: o backend usa rede interna (`http://evolution_api:8585`). Sem `SERVER_URL` correcto na Evolution, versões **v2.2.x** costumam devolver apenas `{"count":0}` no `GET /instance/connect/...`, enquanto o Manager mostra o QR (meta-issue comunitária #2437). Se persistir após configurar isto: testar actualização para **≥ v2.3.7**, `NODE_OPTIONS=--dns-result-order=ipv4first` ou `sysctls` IPv6 conforme VPS, ou workarounds descritos na meta-issue Evolution (cache Redis vs local, histórico de BD, versão WhatsApp cliente).

Opcionalmente, por utilizador, na configuracao de IA (`usuario_ai_config` / endpoint `GET/POST /api/config/ia`):

- `evolution_instance_name` — sobrepõe `EVOLUTION_INSTANCE` para esse utilizador ao pedir QR e estado.
- `evolution_api_key` — sobrepõe `EVOLUTION_APIKEY` se esse utilizador tiver chave dedicada na Evolution.

Opcionalmente, se o QR demorar ou a Evolution devolver `qrcode` vazio nos primeiros segundos, aumente **`EVOLUTION_PAIRING_CONNECT_RETRIES`** e **`EVOLUTION_PAIRING_CONNECT_PAUSE_MS`** (`evolution.pairing.connect.*` em `application.properties`). A Evolution faz internamente espera (~2 s) antes de devolver QR quando o estado está `close`.

Fluxo no frontend **WhatsApp**:

1. Grava o número em `POST /api/usuarios/whatsapp/vincular`.
2. Quando falta parear na Evolution, abre sempre o modal; o mesmo pedido repete até surgir QR ou ficar Connected.
3. Dentro do modal, a cada 5 s: `GET .../evolution-connection-status` e **`POST .../whatsapp/evolution-pairing-refresh`** para atualizar QR/pairing até `connected`.

**Nota:** Se varios utilizadores partilharem **a mesma** instancia Evolution, estado de pareamento (`connected`) e um unico numero WhatsApp Evolution sao globais dessa instancia; o numero **vinculado no perfil** do app continua a servir para identificar o tenant nas mensagens.

### Postgres: memória semântica Jarvis (`memoria_semantica_jarvis`)

O dashboard e projeções podem ler esta tabela. Em arranques sem **pgvector** (`CREATE EXTENSION vector`) ou DDL falhada, antigamente surgia erro 500. O backend tenta criar a tabela no `@PostConstruct` (`SchemaAutoPatchService`); se falhar por falta da extensão, instale pgvector na imagem do Postgres e garanta permissão para `CREATE EXTENSION`, ou crie manualmente a tabela. Enquanto a tabela não existir, o serviço de memória devolve dados vazios em vez de derrubar a API.

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
