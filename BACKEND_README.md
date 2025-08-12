# 🔧 Backend - ConsumoEsperto

API REST desenvolvida em **Spring Boot 2.7.18** com integração bancária e autenticação JWT.

## 🏗️ Arquitetura

### **Stack Tecnológica**
- **Framework**: Spring Boot 2.7.18
- **Java**: JDK 17
- **Build**: Maven 3.9.6
- **Banco**: Oracle Database XE
- **Segurança**: Spring Security + JWT + OAuth2
- **Documentação**: Swagger/OpenAPI
- **Persistência**: Spring Data JPA + Hibernate

### **Estrutura do Projeto**
```
backend/
├── src/main/java/com/consumoesperto/
│   ├── config/              # Configurações (Security, Swagger, WebClient)
│   ├── controller/          # Controladores REST
│   ├── dto/                 # Data Transfer Objects
│   ├── model/               # Entidades JPA
│   ├── repository/          # Repositórios Spring Data
│   ├── security/            # Configurações de segurança
│   └── service/             # Lógica de negócios
├── src/main/resources/
│   ├── application.properties        # Configurações principais
│   ├── bank-apis-config.properties  # Configurações das APIs bancárias
│   └── db/                          # Scripts SQL
└── pom.xml                          # Dependências Maven
```

## 🚀 Execução

### **Setup Automático (Recomendado)**
```bash
# Na raiz do projeto:
setup-completo-projeto.bat
```

### **Execução Manual**
```bash
cd backend

# Compilar
mvn clean compile

# Executar
mvn spring-boot:run

# Ou usar o wrapper Maven
./mvnw spring-boot:run
```

## 🌐 Endpoints da API

### **Autenticação**
- `POST /api/auth/login` - Login com email/senha
- `POST /api/auth/register` - Registro de usuário
- `POST /api/auth/google` - Login com Google OAuth2
- `POST /api/auth/refresh` - Renovar token JWT

### **Usuários**
- `GET /api/usuarios/profile` - Perfil do usuário logado
- `PUT /api/usuarios/profile` - Atualizar perfil
- `DELETE /api/usuarios/profile` - Deletar conta

### **Transações**
- `GET /api/transacoes` - Listar transações
- `POST /api/transacoes` - Criar transação
- `PUT /api/transacoes/{id}` - Atualizar transação
- `DELETE /api/transacoes/{id}` - Deletar transação

### **Cartões de Crédito**
- `GET /api/cartoes` - Listar cartões
- `POST /api/cartoes` - Cadastrar cartão
- `PUT /api/cartoes/{id}` - Atualizar cartão
- `DELETE /api/cartoes/{id}` - Deletar cartão

### **Faturas**
- `GET /api/faturas` - Listar faturas
- `POST /api/faturas` - Criar fatura
- `PUT /api/faturas/{id}` - Atualizar fatura
- `DELETE /api/faturas/{id}` - Deletar fatura

### **Relatórios**
- `GET /api/relatorios/resumo` - Resumo financeiro
- `GET /api/relatorios/categorias` - Relatório por categorias
- `GET /api/relatorios/periodo` - Relatório por período

### **APIs Bancárias**
- `GET /api/bank/itau/accounts` - Contas Itaú
- `GET /api/bank/mercadopago/accounts` - Contas Mercado Pago
- `GET /api/bank/inter/accounts` - Contas Inter
- `GET /api/bank/nubank/accounts` - Contas Nubank

## 🔐 Segurança

### **JWT (JSON Web Token)**
- **Algoritmo**: HS512
- **Expiração**: Configurável via `jwt.expiration-in-ms`
- **Secret**: Configurável via `jwt.secret`

### **OAuth2 para APIs Bancárias**
- **Itaú**: Open Banking
- **Mercado Pago**: API Oficial
- **Inter**: Open Banking
- **Nubank**: Open Banking

### **CORS**
- **Origem**: `http://localhost:4200` (desenvolvimento)
- **Métodos**: GET, POST, PUT, DELETE, OPTIONS
- **Headers**: Authorization, Content-Type

## 🏦 Integração Bancária

### **Configuração Automática**
O sistema configura automaticamente:
- ✅ URLs de callback para OAuth2
- ✅ Configuração do ngrok
- ✅ Variáveis de ambiente

### **Bancos Suportados**

#### **Itaú - Open Banking**
```properties
# application.properties
bank.api.itau.auth-url=https://openbanking.itau.com.br/oauth/authorize
bank.api.itau.token-url=https://openbanking.itau.com.br/oauth/token
bank.api.itau.api-url=https://openbanking.itau.com.br/api
bank.api.itau.scope=openid,profile,email,accounts,transactions
```

#### **Mercado Pago**
```properties
# application.properties
bank.api.mercadopago.auth-url=https://api.mercadopago.com/oauth/token
bank.api.mercadopago.api-url=https://api.mercadopago.com
bank.api.mercadopago.scope=read,write
```

#### **Inter - Open Banking**
```properties
# application.properties
bank.api.inter.auth-url=https://cdp.bancointer.com.br/oauth/authorize
bank.api.inter.token-url=https://cdp.bancointer.com.br/oauth/token
bank.api.inter.api-url=https://cdp.bancointer.com.br/open-banking
bank.api.inter.scope=openid,profile,email,accounts,transactions
```

#### **Nubank**
```properties
# application.properties
bank.api.nubank.auth-url=https://prod-auth.nubank.com.br/oauth/authorize
bank.api.nubank.token-url=https://prod-auth.nubank.com.br/oauth/token
bank.api.nubank.api-url=https://prod-global.nubank.com.br/api
bank.api.nubank.scope=openid,profile,email,accounts,transactions
```

## 🗄️ Banco de Dados

### **Oracle Database XE**
```properties
# application.properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:XE
spring.datasource.username=consumo_esperto
spring.datasource.password=consumo_esperto123
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver

spring.jpa.database-platform=org.hibernate.dialect.Oracle12cDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### **Entidades Principais**
- **Usuario**: Dados do usuário e autenticação
- **Transacao**: Movimentações financeiras
- **Categoria**: Categorização de transações
- **CartaoCredito**: Cartões de crédito
- **Fatura**: Faturas dos cartões
- **CompraParcelada**: Compras parceladas

## 📊 Monitoramento

### **Health Check**
- `GET /actuator/health` - Status da aplicação
- `GET /actuator/info` - Informações da aplicação
- `GET /actuator/metrics` - Métricas da aplicação

### **Logs**
- **Nível**: INFO (configurável)
- **Formato**: JSON estruturado
- **Output**: Console + arquivo (configurável)

## 🔧 Configuração

### **Perfis de Execução**
```bash
# Desenvolvimento
mvn spring-boot:run -Dspring.profiles.active=dev

# Produção
mvn spring-boot:run -Dspring.profiles.active=prod

# Teste
mvn spring-boot:run -Dspring.profiles.active=test
```

### **Variáveis de Ambiente**
```bash
# Credenciais bancárias
ITAU_CLIENT_ID=your_client_id
ITAU_CLIENT_SECRET=your_client_secret
MERCADOPAGO_CLIENT_ID=your_client_id
MERCADOPAGO_CLIENT_SECRET=your_client_secret
INTER_CLIENT_ID=your_client_id
INTER_CLIENT_SECRET=your_client_secret
NUBANK_CLIENT_ID=your_client_id
NUBANK_CLIENT_SECRET=your_client_secret

# URL do ngrok
NGROK_URL=https://your-tunnel.ngrok-free.app
```

## 🧪 Testes

### **Executar Testes**
```bash
# Todos os testes
mvn test

# Testes específicos
mvn test -Dtest=UsuarioServiceTest

# Testes de integração
mvn test -Dtest=*IntegrationTest
```

### **Cobertura de Testes**
```bash
# Gerar relatório de cobertura
mvn jacoco:report
```

## 🐳 Docker

### **Build da Imagem**
```bash
docker build -t consumo-esperto-backend .
```

### **Executar Container**
```bash
docker run -p 8080:8080 consumo-esperto-backend
```

### **Docker Compose**
```bash
# Inclui Oracle Database
docker-compose up -d
```

## 📚 Documentação da API

### **Swagger UI**
- **URL**: `http://localhost:8080/swagger-ui.html`
- **Especificação**: `http://localhost:8080/v3/api-docs`

### **Postman Collection**
- **Arquivo**: `docs/postman/ConsumoEsperto.postman_collection.json`
- **Ambiente**: `docs/postman/ConsumoEsperto.postman_environment.json`

## 🚨 Solução de Problemas

### **Erro de Compilação**
```bash
# Limpar e recompilar
mvn clean compile

# Verificar versão do Java
java -version

# Verificar versão do Maven
mvn -version
```

### **Erro de Conexão com Banco**
```bash
# Verificar se Oracle está rodando
sqlplus system/password@localhost:1521/XE

# Verificar configurações no application.properties
# Verificar firewall/antivírus
```

### **Erro de Porta em Uso**
```bash
# Verificar processos na porta 8080
netstat -ano | findstr :8080

# Parar processo específico
taskkill /PID <PID> /F
```

## 🔄 Atualizações

### **Dependências Maven**
```bash
# Verificar atualizações
mvn versions:display-dependency-updates

# Atualizar dependências
mvn versions:use-latest-versions
```

### **Spring Boot**
```bash
# Atualizar versão do Spring Boot
mvn spring-boot:repackage
```

---

**🎯 Para desenvolvimento, use sempre o setup automático: `setup-completo-projeto.bat`**
