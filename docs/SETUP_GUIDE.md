# Guia de Setup - ConsumoEsperto

Este guia fornece instruções passo a passo para configurar e executar a aplicação ConsumoEsperto.

## Pré-requisitos

### Software Necessário
- **Java 11+** (para o backend Spring Boot)
- **Node.js 18+** (para o frontend Angular)
- **PostgreSQL 12+** (banco de dados)
- **ngrok** (para expor a API localmente)

### Verificação dos Pré-requisitos
```bash
# Verificar Java
java -version

# Verificar Node.js
node --version
npm --version

# Verificar PostgreSQL
psql --version

# Verificar ngrok
ngrok version
```

## Configuração do Banco de Dados

### 1. Instalar PostgreSQL
- **Windows**: Baixe do site oficial ou use Chocolatey
- **Linux**: `sudo apt-get install postgresql postgresql-contrib`
- **macOS**: `brew install postgresql`

### 2. Criar Banco de Dados
```sql
-- Conectar ao PostgreSQL
psql -U postgres

-- Criar banco de dados
CREATE DATABASE consumo_esperto;

-- Criar usuário (opcional)
CREATE USER consumo_user WITH PASSWORD 'senha123';
GRANT ALL PRIVILEGES ON DATABASE consumo_esperto TO consumo_user;

-- Sair
\q
```

### 3. Configurar Backend
Edite o arquivo `backend/src/main/resources/application.properties`:

```properties
# Configurações do Banco de Dados PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/consumo_esperto
spring.datasource.username=postgres
spring.datasource.password=sua_senha_aqui
```

## Setup do Backend

### 1. Navegar para o Diretório
```bash
cd backend
```

### 2. Instalar Dependências
```bash
# Windows
./mvnw.cmd clean install

# Linux/macOS
./mvnw clean install
```

### 3. Executar a Aplicação
```bash
# Windows
./mvnw.cmd spring-boot:run

# Linux/macOS
./mvnw spring-boot:run
```

### 4. Verificar se Está Funcionando
- Acesse: http://localhost:8080
- Documentação da API: http://localhost:8080/swagger-ui.html

## Setup do Frontend

### 1. Navegar para o Diretório
```bash
cd frontend
```

### 2. Instalar Dependências
```bash
npm install
```

### 3. Configurar Ambiente
Crie o arquivo `src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

### 4. Executar a Aplicação
```bash
ng serve
```

### 5. Verificar se Está Funcionando
- Acesse: http://localhost:4200

## Configuração do Ngrok

### 1. Instalar Ngrok
- Baixe de: https://ngrok.com/download
- Extraia e adicione ao PATH

### 2. Configurar Authtoken
```bash
ngrok config add-authtoken SEU_AUTH_TOKEN
```

### 3. Expor a API
```bash
# Com o backend rodando na porta 8080
ngrok http 8080
```

### 4. Configurar Frontend para Ngrok
Após iniciar o ngrok, você receberá uma URL como `https://abc123.ngrok.io`.

Atualize o arquivo `frontend/src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: 'https://abc123.ngrok.io/api'
};
```

## Scripts Úteis

### PowerShell (Windows)
```powershell
# Iniciar ngrok
.\scripts\start-ngrok.ps1

# Iniciar backend
cd backend
.\mvnw.cmd spring-boot:run

# Iniciar frontend
cd frontend
ng serve
```

### Bash (Linux/macOS)
```bash
# Iniciar backend
cd backend && ./mvnw spring-boot:run

# Iniciar frontend
cd frontend && ng serve

# Iniciar ngrok
ngrok http 8080
```

## Estrutura do Projeto

```
ConsumoEsperto/
├── backend/                 # Aplicação Spring Boot
│   ├── src/main/java/
│   │   └── com/consumoesperto/
│   │       ├── controller/  # Controladores REST
│   │       ├── service/     # Lógica de negócios
│   │       ├── repository/  # Repositórios JPA
│   │       ├── model/       # Entidades JPA
│   │       ├── dto/         # Objetos de transferência
│   │       ├── security/    # Configurações de segurança
│   │       └── config/      # Configurações gerais
│   └── src/main/resources/
│       └── application.properties
├── frontend/               # Aplicação Angular
│   ├── src/app/
│   │   ├── components/     # Componentes UI
│   │   ├── services/       # Serviços HTTP
│   │   ├── models/         # Interfaces TypeScript
│   │   └── views/          # Páginas principais
│   └── src/environments/
├── docs/                   # Documentação
├── scripts/                # Scripts de automação
└── README.md
```

## Endpoints da API

### Autenticação
- `POST /api/auth/login` - Login
- `POST /api/auth/registro` - Registro
- `GET /api/auth/me` - Dados do usuário logado

### Transações
- `GET /api/transacoes` - Listar transações
- `POST /api/transacoes` - Criar transação
- `PUT /api/transacoes/{id}` - Atualizar transação
- `DELETE /api/transacoes/{id}` - Deletar transação

### Categorias
- `GET /api/categorias` - Listar categorias
- `POST /api/categorias` - Criar categoria
- `PUT /api/categorias/{id}` - Atualizar categoria
- `DELETE /api/categorias/{id}` - Deletar categoria

## Troubleshooting

### Problemas Comuns

1. **Backend não inicia**
   - Verifique se o PostgreSQL está rodando
   - Verifique as credenciais no `application.properties`
   - Verifique se a porta 8080 está livre

2. **Frontend não carrega**
   - Verifique se o Node.js está instalado
   - Execute `npm install` novamente
   - Verifique se a porta 4200 está livre

3. **Erro de CORS**
   - Configure o CORS no backend
   - Verifique se a URL da API está correta no frontend

4. **Ngrok não funciona**
   - Verifique se o authtoken está configurado
   - Verifique se o backend está rodando na porta 8080
   - Verifique se não há firewall bloqueando

### Logs Úteis

```bash
# Backend logs
tail -f backend/logs/application.log

# Frontend logs
ng serve --verbose

# Ngrok logs
ngrok http 8080 --log=stdout
```

## Próximos Passos

1. **Implementar Funcionalidades**: Adicionar CRUD completo para transações e categorias
2. **Melhorar UI**: Implementar componentes Angular Material
3. **Adicionar Testes**: Implementar testes unitários e de integração
4. **Deploy**: Configurar deploy em produção
5. **Monitoramento**: Adicionar logs e métricas

## Suporte

Para problemas específicos:
- Verifique os logs da aplicação
- Consulte a documentação do Spring Boot e Angular
- Abra uma issue no repositório do projeto
