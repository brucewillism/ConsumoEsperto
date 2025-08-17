@echo off
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
echo     [1] 🗄️  Instalar Oracle 23c (Automático)
echo     [2] 🗄️  Configurar Banco Oracle
echo     [3] 🗄️  Testar Conexão Oracle
echo     [4] 🔐  Configurar Google OAuth2
echo     [5] 💳  Configurar Mercado Pago
echo     [6] 🌐  Configurar NGROK
echo.
echo  🚀 EXECUÇÃO:
echo     [7] 🖥️   Executar Backend (Spring Boot)
echo     [8] 🎨  Executar Frontend (Angular)
echo     [9] 🌐  Executar Backend + NGROK
echo     [10] 🐳 Executar com Docker
echo.
echo  🧪 TESTES E VALIDAÇÃO:
echo     [11] ✅ Testar Compilação Backend
echo     [12] ✅ Testar Compilação Frontend
echo     [13] 🔍 Verificar Status dos Serviços
echo     [14] 📊 Acessar Swagger UI
echo     [15] 💚 Verificar Health Check
echo.
echo  🛠️ FERRAMENTAS:
echo     [16] 📚 Abrir Documentação
echo     [17] 🗂️  Abrir Pasta do Projeto
echo     [18] 🔧 Abrir Configurações
echo     [19] 📝 Ver Logs
echo.
echo  ═══════════════════════════════════════════════════════════════
echo  [0] ❌ Sair
echo.
set /p opcao="Escolha uma opção: "

if "%opcao%"=="1" goto instalar-oracle
if "%opcao%"=="2" goto configurar-oracle
if "%opcao%"=="3" goto testar-oracle
if "%opcao%"=="4" goto configurar-google
if "%opcao%"=="5" goto configurar-mercadopago
if "%opcao%"=="6" goto configurar-ngrok
if "%opcao%"=="7" goto executar-backend
if "%opcao%"=="8" goto executar-frontend
if "%opcao%"=="9" goto executar-backend-ngrok
if "%opcao%"=="10" goto executar-docker
if "%opcao%"=="11" goto testar-backend
if "%opcao%"=="12" goto testar-frontend
if "%opcao%"=="13" goto verificar-servicos
if "%opcao%"=="14" goto acessar-swagger
if "%opcao%"=="15" goto health-check
if "%opcao%"=="16" goto abrir-documentacao
if "%opcao%"=="17" goto abrir-pasta
if "%opcao%"=="18" goto abrir-configuracoes
if "%opcao%"=="19" goto ver-logs
if "%opcao%"=="0" goto sair

echo.
echo  ❌ Opção inválida! Pressione qualquer tecla para continuar...
pause >nul
goto menu

:instalar-oracle
cls
echo.
echo  🗄️ INSTALANDO ORACLE 23c...
echo.
echo  ⚠️  ATENÇÃO: Execute como Administrador!
echo.
echo  📋 O que será instalado:
echo     • Oracle Database 23c Express Edition
echo     • Usuário: consumo_esperto
echo     • Senha: consumo_esperto123
echo     • SID: XE
echo     • Porta: 1521
echo.
echo  🚀 Iniciando instalação automática...
echo.
cd tools
call install-oracle-23c.bat
echo.
echo  ✅ Instalação concluída! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:configurar-oracle
cls
echo.
echo  🗄️ CONFIGURANDO BANCO ORACLE...
echo.
echo  📋 Configurando:
echo     • Usuário do banco
echo     • Tabelas do sistema
echo     • Dados de teste
echo     • Permissões
echo.
cd backend\scripts
call setup-oracle.bat
echo.
echo  ✅ Configuração concluída! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:testar-oracle
cls
echo.
echo  🗄️ TESTANDO CONEXÃO ORACLE...
echo.
echo  📋 Testando:
echo     • Conexão com o banco
echo     • Usuário consumo_esperto
echo     • Tabelas do sistema
echo.
cd tools
call test-oracle-connection.bat
echo.
echo  ✅ Teste concluído! Pressione qualquer tecla para voltar ao menu...
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

:executar-backend
cls
echo.
echo  🖥️ EXECUTANDO BACKEND (SPRING BOOT)...
echo.
echo  📋 Iniciando:
echo     • Spring Boot na porta 8080
echo     • Banco Oracle
echo     • Swagger UI
echo     • Health Check
echo.
echo  🚀 Iniciando backend...
cd backend
start "ConsumoEsperto Backend" mvn spring-boot:run
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
echo  🚀 Iniciando frontend...
cd frontend
start "ConsumoEsperto Frontend" ng serve
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
echo  🚀 Iniciando backend + NGROK...
cd backend
start "ConsumoEsperto Backend" mvn spring-boot:run
timeout /t 10 /nobreak >nul
start "NGROK" ngrok http 8080
echo.
echo  ✅ Backend + NGROK iniciados! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

:executar-docker
cls
echo.
echo  🐳 EXECUTANDO COM DOCKER...
echo.
echo  📋 Iniciando:
echo     • Docker Compose
echo     • Oracle Database
echo     • Spring Boot
echo     • Frontend
echo.
echo  🚀 Iniciando com Docker...
cd backend
docker-compose up -d
echo.
echo  ✅ Docker iniciado! Pressione qualquer tecla para voltar ao menu...
pause >nul
goto menu

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
echo  🚀 Testando backend...
cd backend
mvn clean compile
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
echo  🚀 Testando frontend...
cd frontend
npm install
ng build
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
echo     • Oracle Database
echo     • Spring Boot (porta 8080)
echo     • Angular (porta 4200)
echo     • NGROK
echo.
echo  🚀 Verificando serviços...

echo.
echo  🗄️ Oracle Database:
sc query OracleServiceXE | findstr "STATE"

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
echo     • README-ORACLE-INSTALL.md (Oracle)
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
echo     • Logs do Oracle
echo.
echo  🚀 Verificando logs...

echo.
echo  📁 Logs do Oracle:
if exist "C:\oracle\product\23.0.0\dbhomeXE\RDBMS\LOG" (
    echo  ✅ Pasta de logs encontrada
    dir "C:\oracle\product\23.0.0\dbhomeXE\RDBMS\LOG" | findstr ".log"
) else (
    echo  ❌ Pasta de logs não encontrada
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
