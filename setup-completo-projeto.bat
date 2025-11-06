@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title ConsumoEsperto - Menu Principal

:menu
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║                    🚀 CONSUMO ESPERTO 🚀                    ║
echo  ║                                                              ║
echo  ║                    Sistema de Gestão Financeira             ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
echo  📋 MENU PRINCIPAL:
echo.
echo  🔧 CONFIGURAÇÃO:
echo     [1] 🗄️  Configurar PostgreSQL
echo     [2] 🗄️  Testar Conexão PostgreSQL
echo     [3] ☕ Instalar/Verificar Java 17
echo     [4] 🔐  Configurar Google OAuth2
echo     [5] 💳  Configurar Mercado Pago
echo     [6] 🌐  Configurar NGROK
echo.
echo  🚀 EXECUÇÃO:
echo     [7] 🖥️   Executar Backend (Spring Boot)
echo     [8] 🎨  Executar Frontend (Angular)
echo     [9] 🌐  Executar Backend + NGROK
echo     [10] 📦 Instalar/Verificar Node.js 20
echo     [11] 🏗️  Instalar/Verificar Maven 3.3.9
echo.
echo  🧪 TESTES E VALIDAÇÃO:
echo     [12] ✅ Testar Compilação Backend
echo     [13] ✅ Testar Compilação Frontend
echo     [14] 🔍 Verificar Status dos Serviços
echo     [15] 📊 Acessar Swagger UI
echo     [16] 💚 Verificar Health Check
echo.
echo  🛠️ FERRAMENTAS:
echo     [17] 📚 Abrir Documentação
echo     [18] 🗂️  Abrir Pasta do Projeto
echo     [19] 🔧 Abrir Configurações
echo     [20] 📝 Ver Logs
echo.
echo  ═══════════════════════════════════════════════════════════════
echo  [0] ❌ Sair
echo.
set /p opcao="Escolha uma opção: "

if "%opcao%"=="1" goto configurar-postgres
if "%opcao%"=="2" goto testar-postgres
if "%opcao%"=="3" goto instalar-java
if "%opcao%"=="4" goto configurar-google
if "%opcao%"=="5" goto configurar-mercadopago
if "%opcao%"=="6" goto configurar-ngrok
if "%opcao%"=="7" goto executar-backend
if "%opcao%"=="8" goto executar-frontend
if "%opcao%"=="9" goto executar-backend-ngrok
if "%opcao%"=="10" goto instalar-node
if "%opcao%"=="11" goto instalar-maven
if "%opcao%"=="12" goto testar-backend
if "%opcao%"=="13" goto testar-frontend
if "%opcao%"=="14" goto verificar-servicos
if "%opcao%"=="15" goto acessar-swagger
if "%opcao%"=="16" goto health-check
if "%opcao%"=="17" goto abrir-documentacao
if "%opcao%"=="18" goto abrir-pasta
if "%opcao%"=="19" goto abrir-configuracoes
if "%opcao%"=="20" goto ver-logs
if "%opcao%"=="0" goto sair

echo.
echo  ❌ Opção inválida! Pressione qualquer tecla para continuar...
pause >nul
goto menu

rem ============================================================================
:configurar-postgres
cls
echo.
echo  🗄️ CONFIGURANDO POSTGRESQL...
echo.
echo  📋 O que será feito:
echo     • Criar usuário e banco (se não existirem)
echo     • Conceder permissões
echo     • Criar esquema inicial (DDL)
echo.
echo  🔎 Verificando psql no PATH...
psql --version >nul 2>&1
if %errorlevel% neq 0 (
    echo  ❌ psql nao encontrado. Instale o PostgreSQL e tente novamente.
    echo.
    pause >nul
    goto menu
)

echo  🔎 Testando conexao com usuario postgres...
psql -h localhost -U postgres -d postgres -c "SELECT 1;" >nul 2>&1
if %errorlevel% neq 0 (
    echo  ❌ Nao foi possivel conectar como usuario postgres.
    echo     Verifique se o servico do PostgreSQL esta em execucao e as credenciais.
    echo.
    pause >nul
    goto menu
)

echo  🧩 Criando usuario 'consumo_esperto' (se nao existir)...
psql -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'consumo_esperto') THEN CREATE USER consumo_esperto WITH PASSWORD 'consumo_esperto123'; END IF; END $$;" >nul

echo  🧩 Criando banco 'consumo_esperto' (se nao existir)...
psql -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'consumo_esperto') THEN CREATE DATABASE consumo_esperto OWNER consumo_esperto; END IF; END $$;" >nul

echo  🧩 Concedendo permissoes...
psql -h localhost -U postgres -d consumo_esperto -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE consumo_esperto TO consumo_esperto;" >nul
psql -h localhost -U postgres -d consumo_esperto -v ON_ERROR_STOP=1 -c "GRANT ALL ON SCHEMA public TO consumo_esperto;" >nul

echo  🏗️  Aplicando DDL inicial (backend\\sql\\setup-complete.sql)...
psql -h localhost -U consumo_esperto -d consumo_esperto -v ON_ERROR_STOP=1 -f "backend\\sql\\setup-complete.sql"
if %errorlevel% neq 0 (
    echo.
    echo  ❌ Falha ao aplicar DDL. Verifique o arquivo backend\sql\setup-complete.sql
    echo.
    pause >nul
    goto menu
)

echo.
echo  ✅ PostgreSQL configurado com sucesso!
echo.
pause >nul
goto menu

:testar-postgres
cls
echo.
echo  🗄️ TESTANDO CONEXAO POSTGRESQL...
echo.
echo  🔎 Testando conexao como postgres...
psql -h localhost -U postgres -d postgres -c "SELECT version();" >nul 2>&1
if %errorlevel% neq 0 (
    echo  ❌ Falha ao conectar como postgres.
    echo.
    pause >nul
    goto menu
)

echo  🔎 Testando conexao como consumo_esperto...
psql -h localhost -U consumo_esperto -d consumo_esperto -c "SELECT 1;" >nul 2>&1
if %errorlevel% neq 0 (
    echo  ❌ Falha ao conectar como consumo_esperto. Execute a opcao [1] primeiro.
    echo.
    pause >nul
    goto menu
)

echo  ✅ Conexao OK!
echo.
pause >nul
goto menu

:configurar-google
cls
echo.
echo  🔐 CONFIGURANDO GOOGLE OAUTH2...
echo.
echo  📋 Para configurar o Google OAuth2:
echo.
echo  1. 🌐 Acesse: https://console.cloud.google.com/
echo  2. 🆕 Crie um novo projeto ou selecione existente
echo  3. 🔑 Ative a Google+ API
echo  4. ⚙️  Configure credenciais OAuth2
echo  5. 🔗 Defina redirect URI: [NGROK_URL]/api/auth/google/callback
echo  6. 📝 Configure as variáveis de ambiente:
echo.
echo     GOOGLE_CLIENT_ID=seu_client_id
echo     GOOGLE_CLIENT_SECRET=seu_client_secret
echo.
echo  📚 Consulte a documentação para mais detalhes.
echo.
echo  Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:configurar-mercadopago
cls
echo.
echo  💳 CONFIGURANDO MERCADO PAGO...
echo.
echo  📋 Para configurar o Mercado Pago:
echo.
echo  1. 🌐 Acesse: https://www.mercadopago.com.br/developers
echo  2. 🔑 Configure as credenciais de produção
echo  3. 🔗 Defina as URLs de callback:
echo     • Redirect URI: [NGROK_URL]/api/auth/mercadopago/callback
echo     • Webhook URL: [NGROK_URL]/api/webhooks/mercadopago
echo  4. 📝 Configure as variáveis de ambiente:
echo.
echo     MERCADOPAGO_CLIENT_ID=seu_client_id
echo     MERCADOPAGO_CLIENT_SECRET=seu_client_secret
echo     MERCADOPAGO_USER_ID=seu_user_id
echo.
echo  📚 Consulte a documentação para mais detalhes.
echo.
echo  Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:configurar-ngrok
cls
echo.
echo  🌐 CONFIGURANDO NGROK...
echo.
echo  📋 Para configurar o NGROK:
echo.
echo  1. 📥 Baixe o NGROK: https://ngrok.com/download
echo  2. 🔑 Configure seu authtoken (opcional)
echo  3. 🚀 Execute: ngrok http 8080
echo  4. 🔗 Copie a URL pública gerada
echo  5. 📝 Atualize as configurações com a nova URL
echo.
echo  📚 Consulte a documentação para mais detalhes.
echo.
echo  Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:instalar-java
cls
echo.
echo  ☕ INSTALANDO/VERIFICANDO JAVA 17...
echo.
echo  📋 Verificando versão atual do Java...
echo  🔍 Verificando Java no PATH do sistema...
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo  ✅ Java encontrado no PATH! Verificando versão...
    java -version
    echo.
    echo  🔍 Verificando se é Java 17+...
    for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set java_version=%%i
        set java_version=!java_version:"=!
        echo  Versão encontrada: !java_version!
        if "!java_version!" geq "17" (
            echo  ✅ Java 17+ já está instalado no sistema!
        ) else (
            echo  ⚠️ Java encontrado no sistema, mas versão inferior a 17
            echo  🔍 Verificando se há Java 17+ na pasta tools...
            goto verificar-java-tools
        )
    )
) else (
    echo  ❌ Java não encontrado no PATH do sistema
    echo  🔍 Verificando se há Java na pasta tools...
    goto verificar-java-tools
)

:verificar-java-tools
echo.
echo  🔍 Verificando pasta tools para Java...
if not exist "tools" (
    echo  📁 Criando pasta tools...
    mkdir tools
)

if exist "tools\java\bin\java.exe" (
    echo  ✅ Java encontrado na pasta tools!
    echo  🚀 Configurando PATH local para usar Java da pasta tools...
    set "JAVA_HOME=%CD%\tools\java"
    set "PATH=%CD%\tools\java\bin;%PATH%"
    echo  📋 JAVA_HOME configurado: %JAVA_HOME%
    echo  📋 PATH atualizado com: %CD%\tools\java\bin
    echo.
    echo  🔍 Testando Java da pasta tools...
    "tools\java\bin\java.exe" -version
    echo.
    echo  ✅ Java da pasta tools configurado com sucesso!
) else (
    echo  ❌ Java não encontrado na pasta tools
    echo  📥 Baixando Java 17 automaticamente...
    echo  🚀 Iniciando download automático...
    
    echo  🔧 Instalando Java 17 automaticamente...
    echo  📥 Baixando e instalando via script PowerShell...
    
    if exist "tools\install-tools.ps1" (
        echo  🚀 Executando script de instalação automática...
        powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool java
    ) else (
        echo  ❌ Script de instalação não encontrado
        echo  🔧 Execute o PowerShell como administrador e execute:
        echo  📋 Set-ExecutionPolicy Bypass -Scope CurrentUser -Force
        echo  📋 .\tools\install-tools.ps1 -Tool java
    )
    
    if exist "tools\java\bin\java.exe" (
        echo  ✅ Java 17 baixado e configurado automaticamente!
        echo  🚀 Configurando PATH local...
        set "JAVA_HOME=%CD%\tools\java"
        set "PATH=%CD%\tools\java\bin;%PATH%"
        echo  📋 JAVA_HOME configurado: %JAVA_HOME%
        echo  📋 PATH atualizado com: %CD%\tools\java\bin
        echo.
        echo  🔍 Testando Java da pasta tools...
        "tools\java\bin\java.exe" -version
        echo.
        echo  ✅ Java da pasta tools configurado com sucesso!
    ) else (
        echo  ❌ Falha no download automático do Java
        echo  🔧 Tente executar o script novamente
    )
)
echo.
echo  Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:instalar-node
cls
echo.
echo  📦 INSTALANDO/VERIFICANDO NODE.JS 20...
echo.
echo  📋 Verificando versão atual do Node.js...
echo  🔍 Verificando Node.js no PATH do sistema...
node -v >nul 2>&1
if %errorlevel% equ 0 (
    echo  ✅ Node.js encontrado no PATH! Verificando versão...
    node -v
    echo.
    echo  🔍 Verificando se é Node.js 20+...
    for /f "tokens=1" %%i in ('node -v') do (
        set node_version=%%i
        set node_version=!node_version:v=!
        echo  Versão encontrada: !node_version!
        if "!node_version!" geq "20" (
            echo  ✅ Node.js 20+ já está instalado no sistema!
        ) else (
            echo  ⚠️ Node.js encontrado no sistema, mas versão inferior a 20
            echo  🔍 Verificando se há Node.js 20+ na pasta tools...
            goto verificar-node-tools
        )
    )
) else (
    echo  ❌ Node.js não encontrado no PATH do sistema
    echo  🔍 Verificando se há Node.js na pasta tools...
    goto verificar-node-tools
)

:verificar-node-tools
echo.
echo  🔍 Verificando pasta tools para Node.js...
if not exist "tools" (
    echo  📁 Criando pasta tools...
    mkdir tools
)

if exist "tools\node\node.exe" (
    echo  ✅ Node.js encontrado na pasta tools!
    echo  🚀 Configurando PATH local para usar Node.js da pasta tools...
    set "NODE_HOME=%CD%\tools\node"
    set "PATH=%CD%\tools\node;%PATH%"
    echo  📋 NODE_HOME configurado: %NODE_HOME%
    echo  📋 PATH atualizado com: %CD%\tools\node
    echo.
    echo  🔍 Testando Node.js da pasta tools...
    "tools\node\node.exe" -v
    echo.
    echo  ✅ Node.js da pasta tools configurado com sucesso!
) else (
    echo  ❌ Node.js não encontrado na pasta tools
    echo  📥 Baixando Node.js 20 automaticamente...
    echo  🚀 Iniciando download automático...
    
    echo  🔧 Instalando Node.js 20 automaticamente...
    echo  📥 Baixando e instalando via script PowerShell...
    
    if exist "tools\install-tools.ps1" (
        echo  🚀 Executando script de instalação automática...
        powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool node
    ) else (
        echo  ❌ Script de instalação não encontrado
        echo  🔧 Execute o PowerShell como administrador e execute:
        echo  📋 Set-ExecutionPolicy Bypass -Scope CurrentUser -Force
        echo  📋 .\tools\install-tools.ps1 -Tool node
    )
    
    if exist "tools\node\node.exe" (
        echo  ✅ Node.js 20 baixado e configurado automaticamente!
        echo  🚀 Configurando PATH local...
        set "NODE_HOME=%CD%\tools\node"
        set "PATH=%CD%\tools\node;%PATH%"
        echo  📋 NODE_HOME configurado: %NODE_HOME%
        echo  📋 PATH atualizado com: %CD%\tools\node
        echo.
        echo  🔍 Testando Node.js da pasta tools...
        "tools\node\node.exe" -v
        echo.
        echo  ✅ Node.js da pasta tools configurado com sucesso!
    ) else (
        echo  ❌ Falha no download automático do Node.js
        echo  🔧 Tente executar o script novamente
    )
)
echo.
echo  Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:instalar-maven
cls
echo.
echo  🏗️ INSTALANDO/VERIFICANDO MAVEN 3.3.9...
echo.
echo  📋 Verificando versão atual do Maven...
echo  🔍 Verificando Maven no PATH do sistema...
mvn -version >nul 2>&1
if %errorlevel% equ 0 (
    echo  ✅ Maven encontrado no PATH! Verificando versão...
    mvn -version
    echo.
    echo  🔍 Verificando se é Maven 3.3.9+...
    for /f "tokens=3" %%i in ('mvn -version ^| findstr /i "Apache Maven"') do (
        set maven_version=%%i
        echo  Versão encontrada: !maven_version!
        if "!maven_version!" geq "3.3.9" (
            echo  ✅ Maven 3.3.9+ já está instalado no sistema!
        ) else (
            echo  ⚠️ Maven encontrado no sistema, mas versão inferior a 3.3.9
            echo  🔍 Verificando se há Maven 3.3.9+ na pasta tools...
            goto verificar-maven-tools
        )
    )
) else (
    echo  ❌ Maven não encontrado no PATH do sistema
    echo  🔍 Verificando se há Maven na pasta tools...
    goto verificar-maven-tools
)

:verificar-maven-tools
echo.
echo  🔍 Verificando pasta tools para Maven...
if not exist "tools" (
    echo  📁 Criando pasta tools...
    mkdir tools
)

if exist "tools\maven\bin\mvn.cmd" (
    echo  ✅ Maven encontrado na pasta tools!
    echo  🚀 Configurando PATH local para usar Maven da pasta tools...
    set "MAVEN_HOME=%CD%\tools\maven"
    set "PATH=%CD%\tools\maven\bin;%PATH%"
    echo  📋 MAVEN_HOME configurado: %MAVEN_HOME%
    echo  📋 PATH atualizado com: %CD%\tools\maven\bin
    echo.
    echo  🔍 Testando Maven da pasta tools...
    "tools\maven\bin\mvn.cmd" -version
    echo.
    echo  ✅ Maven da pasta tools configurado com sucesso!
) else (
    echo  ❌ Maven não encontrado na pasta tools
    echo  📥 Baixando Maven 3.9.6 automaticamente...
    echo  🚀 Iniciando download automático...
    
    echo  🔧 Instalando Maven automaticamente...
    echo  📥 Baixando e instalando via script PowerShell...
    
    if exist "tools\install-tools.ps1" (
        echo  🚀 Executando script de instalação automática...
        powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool maven
    ) else (
        echo  ❌ Script de instalação não encontrado
        echo  🔧 Execute o PowerShell como administrador e execute:
        echo  📋 Set-ExecutionPolicy Bypass -Scope CurrentUser -Force
        echo  📋 .\tools\install-tools.ps1 -Tool maven
    )
    
    if exist "tools\maven\bin\mvn.cmd" (
        echo  ✅ Maven 3.9.6 baixado e configurado automaticamente!
        echo  🚀 Configurando PATH local...
        set "MAVEN_HOME=%CD%\tools\maven"
        set "PATH=%CD%\tools\maven\bin;%PATH%"
        echo  📋 MAVEN_HOME configurado: %MAVEN_HOME%
        echo  📋 PATH atualizado com: %CD%\tools\maven\bin
        echo.
        echo  🔍 Testando Maven da pasta tools...
        "tools\maven\bin\mvn.cmd" -version
        echo.
        echo  ✅ Maven da pasta tools configurado com sucesso!
    ) else (
        echo  ❌ Falha no download automático do Maven
        echo  🔧 Tente executar o script novamente
    )
)
echo.
echo  Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:executar-backend
cls
echo.
echo  🖥️ EXECUTANDO BACKEND (SPRING BOOT)...
echo.
echo  📋 Iniciando:
echo     • Spring Boot na porta 8080
echo     • Banco PostgreSQL
echo     • Swagger UI
echo     • Health Check
echo.
echo  🔍 Verificando Maven disponível...
if exist "tools\maven\bin\mvn.cmd" (
    echo  ✅ Usando Maven da pasta tools
    set "MAVEN_CMD=tools\maven\bin\mvn.cmd"
) else (
    echo  ✅ Usando Maven do sistema
    set "MAVEN_CMD=mvn"
)
echo.
echo  🚀 Iniciando backend...
cd backend
start "ConsumoEsperto Backend" %MAVEN_CMD% spring-boot:run -Dspring.profiles.active=postgresql
echo.
echo  ✅ Backend iniciado! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:executar-frontend
cls
echo.
echo  🎨 EXECUTANDO FRONTEND (ANGULAR)...
echo.
echo  📋 Iniciando:
echo     • Angular na porta 4200
echo     • Interface web
echo     • Hot reload
echo.
echo  🔍 Verificando Node.js disponível...
if exist "tools\node\node.exe" (
    echo  ✅ Usando Node.js da pasta tools
    set "NODE_CMD=tools\node\node.exe"
    set "NPM_CMD=tools\node\npm.cmd"
    set "NG_CMD=tools\node\ng.cmd"
) else (
    echo  ✅ Usando Node.js do sistema
    set "NODE_CMD=node"
    set "NPM_CMD=npm"
    set "NG_CMD=ng"
)
echo.
echo  🚀 Iniciando frontend...
cd frontend
start "ConsumoEsperto Frontend" %NG_CMD% serve
echo.
echo  ✅ Frontend iniciado! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:executar-backend-ngrok
cls
echo.
echo  🌐 EXECUTANDO BACKEND + NGROK...
echo.
echo  📋 Iniciando:
echo     • Spring Boot na porta 8080
echo     • NGROK para URL pública
echo     • Swagger UI público
echo.
echo  🔍 Verificando Maven disponível...
if exist "tools\maven\bin\mvn.cmd" (
    echo  ✅ Usando Maven da pasta tools
    set "MAVEN_CMD=tools\maven\bin\mvn.cmd"
) else (
    echo  ✅ Usando Maven do sistema
    set "MAVEN_CMD=mvn"
)
echo.
echo  🚀 Iniciando backend + NGROK...
cd backend
start "ConsumoEsperto Backend" %MAVEN_CMD% spring-boot:run
timeout /t 10 /nobreak >nul
start "NGROK" ngrok http 8080
echo.
echo  ✅ Backend + NGROK iniciados! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

rem Bloco Docker removido

:testar-backend
cls
echo.
echo  ✅ TESTANDO COMPILAÇÃO BACKEND...
echo.
echo  📋 Testando:
echo     • Compilação Maven
echo     • Dependências
echo     • Código fonte
echo.
echo  🔍 Verificando Maven disponível...
if exist "tools\maven\bin\mvn.cmd" (
    echo  ✅ Usando Maven da pasta tools
    set "MAVEN_CMD=tools\maven\bin\mvn.cmd"
) else (
    echo  ✅ Usando Maven do sistema
    set "MAVEN_CMD=mvn"
)
echo.
echo  🚀 Testando backend...
cd backend
%MAVEN_CMD% clean compile
echo.
echo  ✅ Teste concluído! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:testar-frontend
cls
echo.
echo  ✅ TESTANDO COMPILAÇÃO FRONTEND...
echo.
echo  📋 Testando:
echo     • Dependências Node.js
echo     • Compilação Angular
echo     • Código fonte
echo.
echo  🔍 Verificando Node.js disponível...
if exist "tools\node\node.exe" (
    echo  ✅ Usando Node.js da pasta tools
    set "NODE_CMD=tools\node\node.exe"
    set "NPM_CMD=tools\node\npm.cmd"
    set "NG_CMD=tools\node\ng.cmd"
) else (
    echo  ✅ Usando Node.js do sistema
    set "NODE_CMD=node"
    set "NPM_CMD=npm"
    set "NG_CMD=ng"
)
echo.
echo  🚀 Testando frontend...
cd frontend
%NPM_CMD% install
%NG_CMD% build
echo.
echo  ✅ Teste concluído! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:verificar-servicos
cls
echo.
echo  🔍 VERIFICANDO STATUS DOS SERVIÇOS...
echo.
echo  📋 Verificando:
echo     • PostgreSQL (porta 5432)
echo     • Spring Boot (porta 8080)
echo     • Angular (porta 4200)
echo     • NGROK
echo.
echo  🚀 Verificando serviços...

echo.
echo  🗄️ PostgreSQL (porta 5432):
netstat -an | findstr :5432
psql -h localhost -U postgres -d postgres -c "SELECT 1;" >nul 2>&1 && echo  Conexao psql: OK

echo.
echo  🖥️ Spring Boot (porta 8080):
netstat -an | findstr :8080

echo.
echo  🎨 Angular (porta 4200):
netstat -an | findstr :4200

echo.
echo  🌐 NGROK:
netstat -an | findstr :4040

echo.
echo  ✅ Verificação concluída! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:acessar-swagger
cls
echo.
echo  📊 ACESSANDO SWAGGER UI...
echo.
echo  📋 URLs disponíveis:
echo.
echo  🌐 Local: http://localhost:8080/swagger-ui.html
echo  📚 API Docs: http://localhost:8080/api-docs
echo  💚 Health: http://localhost:8080/actuator/health
echo.
echo  🚀 Abrindo Swagger UI...
start http://localhost:8080/swagger-ui.html
echo.
echo  ✅ Swagger UI aberto! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:health-check
cls
echo.
echo  💚 VERIFICANDO HEALTH CHECK...
echo.
echo  📋 Verificando:
echo     • Status da aplicação
echo     • Conexão com banco
echo     • Serviços ativos
echo.
echo  🚀 Verificando health check...
curl -s http://localhost:8080/actuator/health
echo.
echo.
echo  ✅ Health check concluído! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:abrir-documentacao
cls
echo.
echo  📚 ABRINDO DOCUMENTAÇÃO...
echo.
echo  📋 Documentação disponível:
echo     • PROJECT_DOCUMENTATION.md (completa)
echo.
echo  🚀 Abrindo documentação...
start PROJECT_DOCUMENTATION.md
echo.
echo  ✅ Documentação aberta! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:abrir-pasta
cls
echo.
echo  🗂️ ABRINDO PASTA DO PROJETO...
echo.
echo  📋 Pastas disponíveis:
echo     • backend/ (Spring Boot)
echo     • frontend/ (Angular)
echo     • tools/ (Ferramentas)
echo     • scripts/ (Scripts)
echo.
echo  🚀 Abrindo pasta do projeto...
explorer .
echo.
echo  ✅ Pasta aberta! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:abrir-configuracoes
cls
echo.
echo  🔧 ABRINDO CONFIGURAÇÕES...
echo.
echo  📋 Arquivos de configuração:
echo     • application.properties (Spring Boot)
echo     • pom.xml (Maven)
echo     • angular.json (Angular)
echo     • package.json (Node.js)
echo.
echo  🚀 Abrindo configurações...
cd backend\src\main\resources
start application.properties
cd ..\..\..
start backend\pom.xml
start frontend\angular.json
start frontend\package.json
echo.
echo  ✅ Configurações abertas! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:ver-logs
cls
echo.
echo  📝 VERIFICANDO LOGS...
echo.
echo  📋 Logs disponíveis:
echo     • Console da aplicação
echo     • Arquivos de log
echo     • Logs do PostgreSQL
echo.
echo  🚀 Verificando logs...

echo.
echo  📁 Logs do PostgreSQL:
if exist "C:\\Program Files\\PostgreSQL" (
    echo  📂 Diretorio base encontrado em "C:\\Program Files\\PostgreSQL"
    echo  Abra o diretório de dados para consultar os logs (subpasta 'log')
) else (
    echo  ⚠️ Diretorio padrao do PostgreSQL nao encontrado. Verifique sua instalacao.
)

echo.
echo  📁 Logs da aplicação:
if exist "backend\logs" (
    echo  ✅ Pasta de logs encontrada
    dir "backend\logs"
) else (
    echo  ❌ Pasta de logs não encontrada
)

echo.
echo  ✅ Verificação de logs concluída! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:sair
cls
echo.
echo  🎉 OBRIGADO POR USAR O CONSUMO ESPERTO!
echo.
echo  📚 Para mais informações, consulte a documentação:
echo     • PROJECT_DOCUMENTATION.md
echo.
echo  🚀 Desenvolvido com ❤️ para gestão financeira inteligente!
echo.
echo  Pressione qualquer tecla para sair...
pause >nul
exit
