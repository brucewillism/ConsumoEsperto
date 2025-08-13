# 🗄️ SETUP COMPLETO DO ORACLE - CONSUMO ESPERTO

## 🎯 **PROBLEMA IDENTIFICADO**

O erro **"qdo clico no cartao ele vai pra login"** está acontecendo porque:

1. ✅ **Você faz login via Google** (frontend)
2. ❌ **Backend não tem banco Oracle** configurado
3. ❌ **Usuário não é criado/salvo** no banco
4. ❌ **Token JWT não é gerado** pelo backend
5. ❌ **Interceptor não tem token válido** para enviar
6. ❌ **Endpoints bancários retornam 401** (não autorizado)
7. ❌ **Sistema redireciona para login** infinitamente

## 🚀 **SOLUÇÃO: SCRIPTS AUTOMATIZADOS**

### **Opção 1: Setup Completo (RECOMENDADO)**
```bash
# Executar na raiz do projeto
setup-completo-projeto.bat
```

**O que faz:**
- ✅ Baixa e instala Java JDK 17
- ✅ Baixa e instala Maven
- ✅ Baixa e instala Oracle Database 21c XE
- ✅ Cria banco `consumo_esperto` automaticamente
- ✅ Cria usuário com seu nome de login
- ✅ Cria todas as tabelas necessárias
- ✅ Configura frontend Angular
- ✅ Atualiza credenciais do backend

### **Opção 2: Apenas Banco Oracle**
```bash
# Se Oracle já estiver instalado
setup-database.bat
```

**O que faz:**
- ✅ Verifica se Oracle está rodando
- ✅ Cria tablespace e usuário
- ✅ Cria tabelas necessárias
- ✅ Configura credenciais do backend

### **Opção 3: Testar Conexão**
```bash
# Para verificar se tudo está funcionando
testar-banco.bat
```

**O que faz:**
- ✅ Testa conexão Oracle
- ✅ Testa usuário criado
- ✅ Testa compilação do backend
- ✅ Mostra credenciais do banco

## 📋 **PRÉ-REQUISITOS**

- Windows 10/11
- Conexão com internet (para download)
- Permissões de administrador
- ~2GB de espaço livre para Oracle

## 🔧 **CONFIGURAÇÃO AUTOMÁTICA**

### **1. Execute o Setup Completo**
```bash
# Na raiz do projeto
setup-completo-projeto.bat
```

### **2. Aguarde a Instalação**
- Java JDK 17 será baixado e instalado
- Maven será baixado e instalado
- Oracle Database será baixado e instalado
- Banco será criado automaticamente

### **3. Credenciais Criadas**
```
Banco Oracle:
- Usuário: [SEU_NOME_DE_LOGIN]
- Senha: admin123
- Host: localhost
- Porta: 1521
- Service: XE
- Tablespace: consumo_esperto_data
```

## 🎮 **COMO FUNCIONA AGORA**

### **Fluxo de Autenticação Corrigido:**
1. ✅ **Login Google** → Frontend autentica
2. ✅ **Backend recebe** → Google ID token
3. ✅ **Verifica token** → Google OAuth2
4. ✅ **Cria/atualiza usuário** → No banco Oracle
5. ✅ **Gera JWT** → Token de sessão
6. ✅ **Frontend recebe** → JWT válido
7. ✅ **Interceptor adiciona** → JWT nas requisições
8. ✅ **APIs bancárias** → Funcionam normalmente

### **APIs que Funcionarão:**
- ✅ `/api/bank/connected` - Bancos conectados
- ✅ `/api/bank/credit-cards` - Cartões de crédito
- ✅ `/api/bank/invoices` - Faturas
- ✅ `/api/bank/transactions` - Transações

## 🚀 **INICIANDO OS SERVIÇOS**

### **1. Backend (Terminal 1)**
```bash
cd backend
mvn spring-boot:run
```

### **2. Frontend (Terminal 2)**
```bash
cd frontend
ng serve
```

### **3. Acessar Aplicação**
```
http://localhost:4200
```

## 🔑 **CONFIGURAÇÃO DAS APIS BANCÁRIAS**

### **1. Acesse a Configuração**
```
http://localhost:4200/bank-config
```

### **2. Configure Cada Banco**
- **Mercado Pago**: Credenciais de API
- **Nubank**: Token de acesso
- **Itaú**: Open Banking
- **Inter**: Open Banking

### **3. Teste Conexões**
- Use o botão "Testar Conexão" para cada banco
- Configure as credenciais corretas
- Ative os bancos que deseja usar

## 🧪 **TESTANDO TUDO**

### **1. Teste o Banco**
```bash
testar-banco.bat
```

### **2. Teste o Backend**
```bash
cd backend
mvn spring-boot:run
```

### **3. Teste o Frontend**
```bash
cd frontend
ng serve
```

### **4. Teste a Aplicação**
1. Acesse: `http://localhost:4200`
2. Faça login via Google
3. Vá para "Cartões" ou "Faturas"
4. Verifique se não redireciona para login

## 🚨 **RESOLUÇÃO DE PROBLEMAS**

### **Problema: Oracle não inicia**
```bash
# Verificar serviço
sc query OracleServiceXE

# Iniciar serviço
net start OracleServiceXE
```

### **Problema: Backend não compila**
```bash
# Limpar e recompilar
cd backend
mvn clean compile
```

### **Problema: Frontend não roda**
```bash
# Reinstalar dependências
cd frontend
npm install
```

### **Problema: Ainda redireciona para login**
```bash
# Verificar se usuário foi criado
testar-banco.bat

# Verificar logs do backend
# Procurar por erros de conexão com banco
```

## 📚 **ARQUIVOS IMPORTANTES**

### **Scripts de Setup:**
- `setup-completo-projeto.bat` - Setup completo
- `setup-database.bat` - Apenas banco
- `testar-banco.bat` - Teste de conexão

### **Configurações:**
- `backend/src/main/resources/application-dev.properties`
- `frontend/src/environments/environment.ts`

### **Documentação:**
- `SETUP_ORACLE_README.md` - Este arquivo
- `CONFIGURACAO_APIS_BANCARIAS.md` - APIs bancárias
- `GOOGLE_OAUTH_SETUP.md` - Google OAuth2

## 🎉 **RESULTADO ESPERADO**

Após executar o setup:

1. ✅ **Oracle Database** rodando na porta 1521
2. ✅ **Banco `consumo_esperto`** criado
3. ✅ **Usuário com seu nome** criado
4. ✅ **Todas as tabelas** criadas
5. ✅ **Backend conectando** ao banco
6. ✅ **Frontend funcionando** normalmente
7. ✅ **Login Google** criando usuário automaticamente
8. ✅ **APIs bancárias** funcionando sem 401
9. ✅ **Sem redirecionamento** infinito para login

## 🆘 **PRECISA DE AJUDA?**

### **1. Execute o Setup Completo**
```bash
setup-completo-projeto.bat
```

### **2. Teste a Conexão**
```bash
testar-banco.bat
```

### **3. Verifique os Logs**
- Backend: `mvn spring-boot:run`
- Frontend: `ng serve`

### **4. Documentação Adicional**
- Leia os outros READMEs na pasta `docs/`
- Verifique os logs de erro
- Execute os scripts de teste

---

**🎯 Agora você entende exatamente o que estava acontecendo e como resolver!**

**🚀 Execute o `setup-completo-projeto.bat` e tudo funcionará perfeitamente!**
