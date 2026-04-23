# 🔧 Guia de Configuração de Ambiente - ConsumoEsperto

Este guia explica como configurar corretamente o ambiente de desenvolvimento do projeto ConsumoEsperto.

---

## 📋 Índice

1. [Configuração do Java (JAVA_HOME)](#configuração-do-java-java_home)
2. [Variáveis de Ambiente](#variáveis-de-ambiente)
3. [Configuração do Banco de Dados](#configuração-do-banco-de-dados)
4. [Configuração de APIs Bancárias](#configuração-de-apis-bancárias)
5. [Configuração do Ngrok](#configuração-do-ngrok)

---

## ☕ Configuração do Java (JAVA_HOME)

### Problema
O IDE pode não encontrar o JRE System Library, causando erros de compilação.

### Solução Automática

#### Windows (PowerShell)
```powershell
.\configurar-java-home.ps1
```

#### Windows (CMD)
```cmd
configurar-java-home.bat
```

### Solução Manual

#### 1. Configuração Temporária (Sessão Atual)

**PowerShell:**
```powershell
$env:JAVA_HOME = "C:\Users\bruce.silva\Documents\Projetos\ConsumoEsperto\tools\java\ms-17.0.15"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

**CMD:**
```cmd
set JAVA_HOME=C:\Users\bruce.silva\Documents\Projetos\ConsumoEsperto\tools\java\ms-17.0.15
set PATH=%JAVA_HOME%\bin;%PATH%
```

#### 2. Configuração Permanente (Sistema)

1. Abra "Variáveis de Ambiente" do Windows:
   - Pressione `Win + R`
   - Digite: `sysdm.cpl`
   - Clique em "Variáveis de Ambiente"

2. Adicione ou edite:
   - **Nome:** `JAVA_HOME`
   - **Valor:** `C:\Users\bruce.silva\Documents\Projetos\ConsumoEsperto\tools\java\ms-17.0.15`

3. Adicione ao PATH:
   - Adicione: `%JAVA_HOME%\bin`

#### 3. Configuração no IDE (Eclipse/IntelliJ)

**Eclipse:**
1. Window → Preferences → Java → Installed JREs
2. Add → Standard VM
3. JRE home: `C:\Users\bruce.silva\Documents\Projetos\ConsumoEsperto\tools\java\ms-17.0.15`

**IntelliJ IDEA:**
1. File → Project Structure → Project
2. SDK: Add SDK → JDK
3. Selecione: `C:\Users\bruce.silva\Documents\Projetos\ConsumoEsperto\tools\java\ms-17.0.15`

---

## 🔐 Variáveis de Ambiente

### Por que usar variáveis de ambiente?

- ✅ **Segurança:** Credenciais não ficam expostas no código
- ✅ **Flexibilidade:** Diferentes configurações para dev/prod
- ✅ **Boas práticas:** Padrão da indústria

### Configuração Rápida

1. **Copie o arquivo de exemplo:**
   ```bash
   cp .env.example .env
   ```

2. **Edite o arquivo `.env`** com suas credenciais reais

3. **NUNCA commite o arquivo `.env` no Git!**

### Variáveis Necessárias

#### Banco de Dados
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/consumoesperto
DATABASE_USERNAME=seu_usuario
DATABASE_PASSWORD=sua_senha_segura
```

#### JWT (Segurança)
```bash
# Gere uma chave segura: openssl rand -base64 32
JWT_SECRET=sua_chave_secreta_jwt_super_segura_de_pelo_menos_256_bits
```

#### Google OAuth2
```bash
GOOGLE_CLIENT_ID=seu_google_client_id
GOOGLE_CLIENT_SECRET=seu_google_client_secret
```

#### APIs Bancárias
```bash
# Mercado Pago
MERCADOPAGO_CLIENT_ID=seu_client_id
MERCADOPAGO_CLIENT_SECRET=seu_client_secret

# Nubank
NUBANK_CLIENT_ID=seu_client_id
NUBANK_CLIENT_SECRET=seu_client_secret

# Itaú
ITAU_CLIENT_ID=seu_client_id
ITAU_CLIENT_SECRET=seu_client_secret

# Inter
INTER_CLIENT_ID=seu_client_id
INTER_CLIENT_SECRET=seu_client_secret
```

#### Ngrok
```bash
NGROK_URL=https://seu-dominio.ngrok-free.app
NGROK_AUTHTOKEN=seu_ngrok_authtoken
```

---

## 🗄️ Configuração do Banco de Dados

### PostgreSQL Local

1. **Instale o PostgreSQL** (se ainda não tiver)

2. **Crie o banco de dados:**
   ```sql
   CREATE DATABASE consumoesperto;
   CREATE USER seu_usuario WITH PASSWORD 'sua_senha';
   GRANT ALL PRIVILEGES ON DATABASE consumoesperto TO seu_usuario;
   ```

3. **Configure as variáveis de ambiente:**
   ```bash
   export DATABASE_URL=jdbc:postgresql://localhost:5432/consumoesperto
   export DATABASE_USERNAME=seu_usuario
   export DATABASE_PASSWORD=sua_senha
   ```

4. **Execute as migrações Flyway:**
   ```bash
   cd backend
   mvn flyway:migrate
   ```

---

## 🏦 Configuração de APIs Bancárias

### Mercado Pago

1. **Acesse:** https://www.mercadopago.com.br/developers
2. **Crie uma aplicação**
3. **Obtenha:**
   - Client ID
   - Client Secret
   - User ID

4. **Configure as variáveis:**
   ```bash
   export MERCADOPAGO_CLIENT_ID=seu_client_id
   export MERCADOPAGO_CLIENT_SECRET=seu_client_secret
   export MERCADOPAGO_USER_ID=seu_user_id
   ```

### Nubank

1. **Acesse:** https://developer.nubank.com.br/
2. **Registre sua aplicação**
3. **Obtenha:**
   - Client ID
   - Client Secret

4. **Configure as variáveis:**
   ```bash
   export NUBANK_CLIENT_ID=seu_client_id
   export NUBANK_CLIENT_SECRET=seu_client_secret
   ```

### Itaú / Inter

Siga o mesmo processo para cada banco, obtendo as credenciais OAuth2 e configurando as variáveis de ambiente correspondentes.

---

## 🌐 Configuração do Ngrok

### Instalação

O ngrok já está disponível em `tools/tools/ngrok/ngrok.exe`

### Configuração

1. **Obtenha seu authtoken:**
   - Acesse: https://dashboard.ngrok.com/get-started/your-authtoken
   - Copie seu authtoken

2. **Configure a variável:**
   ```bash
   export NGROK_AUTHTOKEN=seu_authtoken
   ```

3. **Inicie o ngrok:**
   ```bash
   tools\tools\ngrok\ngrok.exe http 8080
   ```

4. **Copie a URL pública** (ex: `https://abc123.ngrok-free.app`)

5. **Configure a variável:**
   ```bash
   export NGROK_URL=https://abc123.ngrok-free.app
   ```

### Detecção Automática

O projeto pode detectar automaticamente a URL do ngrok se você:
- Tiver o ngrok rodando na porta 4040
- Configurar: `NGROK_AUTO_DETECT=true`

---

## ✅ Verificação

### Verificar Java
```bash
java -version
javac -version
```

### Verificar Variáveis de Ambiente
```bash
# Windows PowerShell
Get-ChildItem Env: | Where-Object Name -like "*DATABASE*"
Get-ChildItem Env: | Where-Object Name -like "*JWT*"

# Windows CMD
set | findstr DATABASE
set | findstr JWT
```

### Testar Conexão com Banco
```bash
cd backend
mvn spring-boot:run
```

---

## 🚨 Problemas Comuns

### Erro: "JRE System Library não encontrado"
**Solução:** Execute `configurar-java-home.bat` ou configure manualmente o JAVA_HOME

### Erro: "Credenciais inválidas"
**Solução:** Verifique se as variáveis de ambiente estão configuradas corretamente

### Erro: "Conexão com banco de dados falhou"
**Solução:** 
- Verifique se o PostgreSQL está rodando
- Verifique se as credenciais estão corretas
- Verifique se o banco de dados existe

### Erro: "Ngrok URL não encontrada"
**Solução:**
- Inicie o ngrok: `tools\tools\ngrok\ngrok.exe http 8080`
- Configure `NGROK_URL` com a URL fornecida

---

## 📚 Referências

- [Documentação Spring Boot](https://spring.io/projects/spring-boot)
- [Documentação PostgreSQL](https://www.postgresql.org/docs/)
- [Documentação Ngrok](https://ngrok.com/docs)
- [OAuth2 Flow](https://oauth.net/2/)

---

**Última atualização:** 2025-01-27

