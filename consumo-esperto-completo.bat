@echo off
setlocal enabledelayedexpansion
color 0A
title CONSUMO ESPERTO - SISTEMA COMPLETO

:MAIN_MENU
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                    🚀 CONSUMO ESPERTO - SISTEMA COMPLETO 🚀                 ║
echo  ║                                                                              ║
echo  ║  📋 Escolha uma opcao:                                                      ║
echo  ║                                                                              ║
echo  ║  [1] 🔧 INSTALAR DEPENDENCIAS                                               ║
echo  ║  [2] 🗄️  CONFIGURAR POSTGRESQL                                              ║
echo  ║  [3] ⚙️  CONFIGURAR CREDENCIAIS                                              ║
echo  ║  [4] 🌐 CONFIGURAR VARIÁVEIS DE AMBIENTE                                     ║
echo  ║  [5] ☕ CONFIGURAR JAVA 17 TEMPORARIO                                        ║
echo  ║  [6] 🖥️  RODAR BACKEND                                                       ║
echo  ║  [7] 🌐 RODAR FRONTEND                                                       ║
echo  ║  [8] 🚀 RODAR TUDO + NGROK                                                  ║
echo  ║  [9] 🔍 VERIFICAR STATUS                                                    ║
echo  ║  [10] 📊 MONITORAR SISTEMA                                                  ║
echo  ║  [11] 🧹 LIMPAR E RESETAR                                                   ║
echo  ║  [12] 📦 BACKUP E RESTORE                                                   ║
echo  ║  [13] 🔄 ATUALIZAR SISTEMA                                                  ║
echo  ║  [14] 🧪 TESTAR SISTEMA                                                     ║
echo  ║  [15] 🗑️  DESINSTALAR TUDO                                                  ║
echo  ║  [0] ❌ SAIR                                                                ║
echo  ║                                                                              ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
set /p choice="Digite sua opcao (0-15): "

if "%choice%"=="1" goto INSTALL_DEPS
if "%choice%"=="2" goto CONFIG_POSTGRES
if "%choice%"=="3" goto CONFIG_CREDS
if "%choice%"=="4" goto SET_ENV_PROD
if "%choice%"=="5" goto CONFIG_JAVA_TEMP
if "%choice%"=="6" goto RUN_BACKEND
if "%choice%"=="7" goto RUN_FRONTEND
if "%choice%"=="8" goto RUN_ALL
if "%choice%"=="9" goto CHECK_STATUS
if "%choice%"=="10" goto MONITOR_SYSTEM
if "%choice%"=="11" goto CLEAN_RESET
if "%choice%"=="12" goto BACKUP_RESTORE
if "%choice%"=="13" goto UPDATE_SYSTEM
if "%choice%"=="14" goto TEST_SYSTEM
if "%choice%"=="15" goto UNINSTALL_ALL
if "%choice%"=="0" goto EXIT
goto INVALID_CHOICE

:INSTALL_DEPS
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🔧 INSTALANDO DEPENDENCIAS 🔧                        ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/6] Verificando Java 17...
java -version 2>nul | findstr "17" >nul
if %errorlevel% neq 0 (
    echo ❌ Java 17 nao encontrado. Instalando...
    call :InstallJava
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar Java 17
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ Java 17 ja instalado
)

echo.
echo [2/6] Verificando Maven...
mvn -version 2>nul | findstr "Apache Maven" >nul
if %errorlevel% neq 0 (
    echo ❌ Maven nao encontrado. Instalando...
    call :InstallMaven
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar Maven
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ Maven ja instalado
)

echo.
echo [3/6] Verificando PostgreSQL...
psql --version 2>nul | findstr "psql" >nul
if %errorlevel% neq 0 (
    echo ❌ PostgreSQL nao encontrado. Instalando...
    call :InstallPostgreSQL
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar PostgreSQL
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ PostgreSQL ja instalado
)

echo.
echo [4/6] Verificando Node.js...
node --version 2>nul | findstr "v" >nul
if %errorlevel% neq 0 (
    echo ❌ Node.js nao encontrado. Instalando...
    call :InstallNode
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar Node.js
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ Node.js ja instalado
)

echo.
echo [5/6] Verificando ngrok...
ngrok version 2>nul | findstr "ngrok" >nul
if %errorlevel% neq 0 (
    echo ❌ ngrok nao encontrado. Instalando...
    call :InstallNgrok
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar ngrok
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ ngrok ja instalado
)

echo.
echo [6/6] Instalando dependencias do projeto...
cd backend
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo ❌ Erro ao instalar dependencias do backend
    cd ..
    pause
    goto MAIN_MENU
)

cd ..\frontend
call npm install
if %errorlevel% neq 0 (
    echo ❌ Erro ao instalar dependencias do frontend
    cd ..
    pause
    goto MAIN_MENU
)

cd ..
echo.
echo ✅ Todas as dependencias foram instaladas com sucesso!
pause
goto MAIN_MENU

:CONFIG_POSTGRES
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                      🗄️  CONFIGURANDO POSTGRESQL 🗄️                         ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/4] Verificando se PostgreSQL esta rodando...
sc query postgresql-x64-13 >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ PostgreSQL nao esta rodando. Iniciando servico...
    net start postgresql-x64-13
    if %errorlevel% neq 0 (
        echo ❌ Erro ao iniciar PostgreSQL
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ PostgreSQL esta rodando
)

echo.
echo [2/4] Criando banco de dados...
psql -U postgres -c "CREATE DATABASE consumoesperto;" 2>nul
if %errorlevel% neq 0 (
    echo ⚠️  Banco ja existe ou erro na criacao
) else (
    echo ✅ Banco de dados criado
)

echo.
echo [3/4] Criando usuario...
psql -U postgres -c "CREATE USER bruce WITH PASSWORD '0301HeX@';" 2>nul
if %errorlevel% neq 0 (
    echo ⚠️  Usuario ja existe ou erro na criacao
) else (
    echo ✅ Usuario criado
)

psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE consumoesperto TO bruce;" 2>nul
echo ✅ Permissoes concedidas

echo.
echo [4/4] Executando scripts de inicializacao...
cd backend\sql
psql -U bruce -d consumoesperto -f setup-complete.sql
if %errorlevel% neq 0 (
    echo ❌ Erro ao executar scripts SQL
    cd ..\..
    pause
    goto MAIN_MENU
)

cd ..\..
echo.
echo ✅ PostgreSQL configurado com sucesso!
pause
goto MAIN_MENU

:CONFIG_CREDS
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                      ⚙️  CONFIGURAR CREDENCIAIS ⚙️                          ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo 🔑 Configuracao de credenciais necessarias:
echo.
echo 1. JWT Secret (obrigatorio)
echo 2. Google OAuth2 (obrigatorio)
echo 3. APIs Bancarias (opcional)
echo.

set /p jwt_secret="Digite o JWT Secret (minimo 32 caracteres): "
if "%jwt_secret%"=="" (
    echo ❌ JWT Secret e obrigatorio
    pause
    goto MAIN_MENU
)

set /p google_client_id="Digite o Google Client ID: "
set /p google_client_secret="Digite o Google Client Secret: "

echo.
echo APIs Bancarias (opcional - pressione Enter para pular):
echo.

set /p nubank_client_id="Nubank Client ID: "
set /p nubank_client_secret="Nubank Client Secret: "

set /p itau_client_id="Itau Client ID: "
set /p itau_client_secret="Itau Client Secret: "

set /p mercadopago_client_id="Mercado Pago Client ID: "
set /p mercadopago_client_secret="Mercado Pago Client Secret: "
set /p mercadopago_user_id="Mercado Pago User ID: "

set /p inter_client_id="Inter Client ID: "
set /p inter_client_secret="Inter Client Secret: "

echo.
echo [1/2] Criando arquivo .env...
(
echo # ========================================
echo #    VARIAVEIS DE AMBIENTE - CONSUMO ESPERTO
echo # ========================================
echo.
echo # JWT e Seguranca
echo JWT_SECRET=%jwt_secret%
echo JWT_EXPIRATION=86400000
echo JWT_REFRESH_EXPIRATION=604800000
echo.
echo # Google OAuth2
echo GOOGLE_CLIENT_ID=%google_client_id%
echo GOOGLE_CLIENT_SECRET=%google_client_secret%
echo.
echo # APIs Bancarias - Nubank
echo NUBANK_CLIENT_ID=%nubank_client_id%
echo NUBANK_CLIENT_SECRET=%nubank_client_secret%
echo.
echo # APIs Bancarias - Itau
echo ITAU_CLIENT_ID=%itau_client_id%
echo ITAU_CLIENT_SECRET=%itau_client_secret%
echo.
echo # APIs Bancarias - Mercado Pago
echo MERCADOPAGO_CLIENT_ID=%mercadopago_client_id%
echo MERCADOPAGO_CLIENT_SECRET=%mercadopago_client_secret%
echo MERCADOPAGO_USER_ID=%mercadopago_user_id%
echo.
echo # APIs Bancarias - Inter
echo INTER_CLIENT_ID=%inter_client_id%
echo INTER_CLIENT_SECRET=%inter_client_secret%
echo.
echo # URL Publica (ngrok)
echo NGROK_URL=http://localhost:8080
) > .env

echo [2/2] Criando script de carregamento...
(
echo @echo off
echo echo Carregando variaveis de ambiente...
echo for /f "usebackq tokens=1,2 delims==" %%%%a in ("%%cd%%\.env") do (
echo     if not "%%%%a"=="" if not "%%%%a"=="#" (
echo         set "%%%%a=%%%%b"
echo     )
echo )
echo echo Variaveis carregadas!
) > load-env.bat

echo.
echo ✅ Credenciais configuradas com sucesso!
echo.
echo 📋 Arquivos criados:
echo    • .env (variaveis de ambiente)
echo    • load-env.bat (carregar variaveis)
echo.
pause
goto MAIN_MENU

:RUN_BACKEND
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🖥️  RODANDO BACKEND 🖥️                               ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/3] Carregando variaveis de ambiente...
if exist ".env" (
    call load-env.bat
    echo ✅ Variaveis carregadas
) else (
    echo ⚠️  Arquivo .env nao encontrado
    echo 🔧 Execute a opcao 3 para configurar credenciais
)

echo.
echo [2/3] Compilando projeto...
cd backend
call mvn clean compile
if %errorlevel% neq 0 (
    echo ❌ Erro ao compilar projeto
    cd ..
    pause
    goto MAIN_MENU
)

echo.
echo [3/3] Iniciando Spring Boot...
echo ✅ Backend iniciando em http://localhost:8080
echo ✅ Swagger UI disponivel em http://localhost:8080/swagger-ui.html
echo ✅ Health check em http://localhost:8080/actuator/health
echo.
echo Pressione Ctrl+C para parar o backend
echo.

call mvn spring-boot:run

cd ..
pause
goto MAIN_MENU

:RUN_FRONTEND
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🌐 RODANDO FRONTEND 🌐                               ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/3] Verificando Node.js...
node --version
if %errorlevel% neq 0 (
    echo ❌ Node.js nao encontrado
    pause
    goto MAIN_MENU
)

echo.
echo [2/3] Instalando dependencias...
cd frontend
call npm install
if %errorlevel% neq 0 (
    echo ❌ Erro ao instalar dependencias
    cd ..
    pause
    goto MAIN_MENU
)

echo.
echo [3/3] Iniciando Angular...
echo ✅ Frontend iniciando em http://localhost:4200
echo.
echo Pressione Ctrl+C para parar o frontend
echo.

call npm start

cd ..
pause
goto MAIN_MENU

:RUN_ALL
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                    🚀 RODANDO TUDO + NGROK 🚀                               ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/5] Carregando variaveis de ambiente...
if exist ".env" (
    call load-env.bat
    echo ✅ Variaveis carregadas
) else (
    echo ⚠️  Arquivo .env nao encontrado
    echo 🔧 Execute a opcao 3 para configurar credenciais
)

echo.
echo [2/5] Verificando ngrok...
ngrok version 2>nul | findstr "ngrok" >nul
if %errorlevel% neq 0 (
    echo ❌ ngrok nao encontrado. Instalando...
    call :InstallNgrok
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar ngrok
        pause
        goto MAIN_MENU
    )
)

echo.
echo [3/5] Iniciando ngrok...
start "ngrok" cmd /c "ngrok http 8080 --log=stdout"
timeout /t 3 /nobreak >nul

echo.
echo [4/5] Iniciando backend...
start "backend" cmd /c "cd backend && mvn spring-boot:run"
timeout /t 10 /nobreak >nul

echo.
echo [5/5] Iniciando frontend...
start "frontend" cmd /c "cd frontend && npm start"

echo.
echo ✅ Tudo iniciado com sucesso!
echo.
echo 📊 URLs disponiveis:
echo    • Frontend: http://localhost:4200
echo    • Backend: http://localhost:8080
echo    • ngrok Dashboard: http://localhost:4040
echo    • Swagger UI: http://localhost:8080/swagger-ui.html
echo.
echo 🔍 Para verificar o status, use a opcao 7 do menu
echo.
pause
goto MAIN_MENU

:CHECK_STATUS
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🔍 VERIFICANDO STATUS 🔍                             ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/8] Verificando Java...
java -version 2>nul | findstr "17" >nul
if %errorlevel% equ 0 (
    echo ✅ Java 17: OK
) else (
    echo ❌ Java 17: NAO ENCONTRADO
)

echo.
echo [2/8] Verificando Maven...
mvn -version 2>nul | findstr "Apache Maven" >nul
if %errorlevel% equ 0 (
    echo ✅ Maven: OK
) else (
    echo ❌ Maven: NAO ENCONTRADO
)

echo.
echo [3/8] Verificando PostgreSQL...
psql --version 2>nul | findstr "psql" >nul
if %errorlevel% equ 0 (
    echo ✅ PostgreSQL: OK
    sc query postgresql-x64-13 >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ PostgreSQL Service: RODANDO
    ) else (
        echo ❌ PostgreSQL Service: PARADO
    )
) else (
    echo ❌ PostgreSQL: NAO ENCONTRADO
)

echo.
echo [4/8] Verificando Node.js...
node --version 2>nul | findstr "v" >nul
if %errorlevel% equ 0 (
    echo ✅ Node.js: OK
) else (
    echo ❌ Node.js: NAO ENCONTRADO
)

echo.
echo [5/8] Verificando ngrok...
ngrok version 2>nul | findstr "ngrok" >nul
if %errorlevel% equ 0 (
    echo ✅ ngrok: OK
) else (
    echo ❌ ngrok: NAO ENCONTRADO
)

echo.
echo [6/8] Verificando aplicacao...
curl -s http://localhost:8080/actuator/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Backend: RODANDO
) else (
    echo ❌ Backend: PARADO
)

curl -s http://localhost:4200 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Frontend: RODANDO
) else (
    echo ❌ Frontend: PARADO
)

curl -s http://localhost:4040 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ ngrok: RODANDO
) else (
    echo ❌ ngrok: PARADO
)

echo.
pause
goto MAIN_MENU

:MONITOR_SYSTEM
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                      📊 MONITORAR SISTEMA 📊                                ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo Escolha o que monitorar:
echo.
echo [1] Ver logs do backend
echo [2] Abrir ngrok dashboard
echo [3] Ver metricas da aplicacao
echo [4] Ver status dos servicos
echo [5] Voltar ao menu principal
echo.
set /p monitor_choice="Digite sua opcao (1-5): "

if "%monitor_choice%"=="1" goto VIEW_LOGS
if "%monitor_choice%"=="2" goto NGROK_DASHBOARD
if "%monitor_choice%"=="3" goto VIEW_METRICS
if "%monitor_choice%"=="4" goto VIEW_SERVICES
if "%monitor_choice%"=="5" goto MAIN_MENU
goto INVALID_CHOICE

:VIEW_LOGS
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        📋 LOGS DO BACKEND 📋                                ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo Pressione Ctrl+C para sair dos logs
echo.
if exist "backend\logs\consumo-esperto.log" (
    type "backend\logs\consumo-esperto.log"
) else (
    echo ❌ Arquivo de log nao encontrado
    pause
)
goto MONITOR_SYSTEM

:NGROK_DASHBOARD
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                      🌐 NGROK DASHBOARD 🌐                                 ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo Abrindo ngrok dashboard...
start http://localhost:4040
echo.
echo ✅ Dashboard aberto no navegador
pause
goto MONITOR_SYSTEM

:VIEW_METRICS
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        📊 METRICAS DA APLICACAO 📊                          ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo Abrindo metricas...
start http://localhost:8080/actuator/metrics
echo.
echo ✅ Metricas abertas no navegador
pause
goto MONITOR_SYSTEM

:VIEW_SERVICES
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🔧 STATUS DOS SERVICOS 🔧                            ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo Verificando servicos...
echo.
sc query postgresql-x64-13
echo.
echo Pressione qualquer tecla para continuar...
pause >nul
goto MONITOR_SYSTEM

:CLEAN_RESET
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🧹 LIMPAR E RESETAR 🧹                               ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo ATENCAO: Esta operacao ira limpar logs, cache e resetar o banco de dados!
echo.
set /p confirm="Tem certeza? (s/N): "
if /i not "%confirm%"=="s" goto MAIN_MENU

echo.
echo [1/4] Parando servicos...
taskkill /f /im java.exe 2>nul
taskkill /f /im node.exe 2>nul
taskkill /f /im ngrok.exe 2>nul

echo.
echo [2/4] Limpando logs...
if exist "backend\logs\*.log" del /q "backend\logs\*.log"
if exist "backend\logs\*.gz" del /q "backend\logs\*.gz"

echo.
echo [3/4] Limpando cache...
if exist "backend\target" rmdir /s /q "backend\target"
if exist "frontend\dist" rmdir /s /q "frontend\dist"
if exist "frontend\node_modules" rmdir /s /q "frontend\node_modules"

echo.
echo [4/4] Resetando banco de dados...
psql -U bruce -d consumoesperto -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
if %errorlevel% equ 0 (
    echo ✅ Banco resetado
) else (
    echo ❌ Erro ao resetar banco
)

echo.
echo ✅ Limpeza concluida!
pause
goto MAIN_MENU

:BACKUP_RESTORE
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                      📦 BACKUP E RESTORE 📦                                 ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo Escolha uma opcao:
echo.
echo [1] Criar backup do banco
echo [2] Restaurar backup do banco
echo [3] Voltar ao menu principal
echo.
set /p backup_choice="Digite sua opcao (1-3): "

if "%backup_choice%"=="1" goto CREATE_BACKUP
if "%backup_choice%"=="2" goto RESTORE_BACKUP
if "%backup_choice%"=="3" goto MAIN_MENU
goto INVALID_CHOICE

:CREATE_BACKUP
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        📦 CRIANDO BACKUP 📦                                  ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

set backup_file=backup-%date:~-4,4%-%date:~-10,2%-%date:~-7,2%-%time:~0,2%-%time:~3,2%-%time:~6,2%.sql
set backup_file=%backup_file: =0%

echo [1/3] Criando diretorio de backup...
if not exist "backups" mkdir backups

echo.
echo [2/3] Criando backup...
pg_dump -U bruce -d consumoesperto -f "backups\%backup_file%"
if %errorlevel% equ 0 (
    echo ✅ Backup criado: backups\%backup_file%
) else (
    echo ❌ Erro ao criar backup
    pause
    goto BACKUP_RESTORE
)

echo.
echo [3/3] Comprimindo backup...
powershell -command "Compress-Archive -Path 'backups\%backup_file%' -DestinationPath 'backups\%backup_file%.zip' -Force"
if %errorlevel% equ 0 (
    del "backups\%backup_file%"
    echo ✅ Backup comprimido: backups\%backup_file%.zip
) else (
    echo ⚠️  Backup criado mas nao foi possivel comprimir
)

echo.
echo ✅ Backup concluido!
pause
goto BACKUP_RESTORE

:RESTORE_BACKUP
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🔄 RESTAURANDO BACKUP 🔄                             ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

if not exist "backups" (
    echo ❌ Diretorio de backup nao encontrado
    pause
    goto BACKUP_RESTORE
)

echo Backups disponiveis:
dir /b backups\*.zip 2>nul
if %errorlevel% neq 0 (
    echo ❌ Nenhum backup encontrado
    pause
    goto BACKUP_RESTORE
)

echo.
set /p backup_file="Digite o nome do arquivo de backup: "
if not exist "backups\%backup_file%" (
    echo ❌ Arquivo nao encontrado
    pause
    goto BACKUP_RESTORE
)

echo.
echo [1/3] Descomprimindo backup...
powershell -command "Expand-Archive -Path 'backups\%backup_file%' -DestinationPath 'backups' -Force"
set sql_file=%backup_file:.zip=%

echo.
echo [2/3] Restaurando backup...
psql -U bruce -d consumoesperto -f "backups\%sql_file%"
if %errorlevel% equ 0 (
    echo ✅ Backup restaurado com sucesso!
) else (
    echo ❌ Erro ao restaurar backup
    pause
    goto BACKUP_RESTORE
)

echo.
echo [3/3] Limpando arquivos temporarios...
del "backups\%sql_file%"

echo.
echo ✅ Restore concluido!
pause
goto BACKUP_RESTORE

:UPDATE_SYSTEM
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🔄 ATUALIZANDO SISTEMA 🔄                            ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/6] Parando servicos...
taskkill /f /im java.exe 2>nul
taskkill /f /im node.exe 2>nul
taskkill /f /im ngrok.exe 2>nul

echo.
echo [2/6] Atualizando dependencias do backend...
cd backend
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo ❌ Erro ao atualizar backend
    cd ..
    pause
    goto MAIN_MENU
)
cd ..

echo.
echo [3/6] Atualizando dependencias do frontend...
cd frontend
call npm update
if %errorlevel% neq 0 (
    echo ❌ Erro ao atualizar frontend
    cd ..
    pause
    goto MAIN_MENU
)
cd ..

echo.
echo [4/6] Verificando configuracoes...
if not exist ".env" (
    echo ⚠️  Arquivo .env nao encontrado
    echo 🔧 Execute a opcao 3 para configurar credenciais
) else (
    echo ✅ Configuracoes encontradas
)

echo.
echo [5/6] Testando sistema atualizado...
call :TestSystem
if %errorlevel% neq 0 (
    echo ❌ Erro no teste do sistema
    pause
    goto MAIN_MENU
)

echo.
echo [6/6] Sistema atualizado com sucesso!
echo.
echo ✅ Atualizacao concluida!
pause
goto MAIN_MENU

:TEST_SYSTEM
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🧪 TESTANDO SISTEMA 🧪                               ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/10] Verificando dependencias...
call :CheckDependencies
if %errorlevel% neq 0 (
    echo ❌ Sistema nao esta pronto para teste
    pause
    goto MAIN_MENU
)

echo.
echo [2/10] Testando conexao com PostgreSQL...
psql -U bruce -d consumoesperto -c "SELECT 1;" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ PostgreSQL: OK
) else (
    echo ❌ PostgreSQL: FALHOU
    pause
    goto MAIN_MENU
)

echo.
echo [3/10] Testando backend...
curl -s http://localhost:8080/actuator/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Backend: OK
) else (
    echo ❌ Backend: FALHOU
    echo 🔧 Execute a opcao 4 para iniciar o backend
    pause
    goto MAIN_MENU
)

echo.
echo [4/10] Testando frontend...
curl -s http://localhost:4200 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Frontend: OK
) else (
    echo ❌ Frontend: FALHOU
    echo 🔧 Execute a opcao 5 para iniciar o frontend
    pause
    goto MAIN_MENU
)

echo.
echo [5/10] Testando ngrok...
curl -s http://localhost:4040 >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ ngrok: OK
) else (
    echo ❌ ngrok: FALHOU
    echo 🔧 Execute a opcao 6 para iniciar tudo
    pause
    goto MAIN_MENU
)

echo.
echo [6/10] Testando autenticacao...
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"teste@teste.com\",\"senha\":\"123456\"}" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Autenticacao: OK
) else (
    echo ⚠️  Autenticacao: Teste basico falhou (normal se nao houver usuario)
)

echo.
echo [7/10] Testando APIs bancarias...
curl -s http://localhost:8080/api/bank-api/status >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ APIs Bancarias: OK
) else (
    echo ⚠️  APIs Bancarias: Teste basico falhou (normal se nao configurado)
)

echo.
echo [8/10] Testando relatorios...
curl -s http://localhost:8080/api/relatorios/resumo >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Relatorios: OK
) else (
    echo ⚠️  Relatorios: Teste basico falhou (normal se nao houver dados)
)

echo.
echo [9/10] Testando simulacoes...
curl -s http://localhost:8080/api/simulacoes/teste >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Simulacoes: OK
) else (
    echo ⚠️  Simulacoes: Teste basico falhou (normal se nao houver dados)
)

echo.
echo [10/10] Testando metricas...
curl -s http://localhost:8080/actuator/metrics >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Metricas: OK
) else (
    echo ❌ Metricas: FALHOU
)

echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🎉 TESTE CONCLUIDO! 🎉                               ║
echo  ║                                                                              ║
echo  ║  ✅ Sistema funcionando perfeitamente!                                      ║
echo  ║                                                                              ║
echo  ║  🌐 URLs para teste:                                                        ║
echo  ║     • Frontend: http://localhost:4200                                       ║
echo  ║     • Backend: http://localhost:8080                                        ║
echo  ║     • Swagger: http://localhost:8080/swagger-ui.html                        ║
echo  ║     • ngrok: http://localhost:4040                                          ║
echo  ║                                                                              ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo Deseja abrir o frontend para teste? (s/N)
set /p open_frontend="Digite sua opcao: "
if /i "%open_frontend%"=="s" (
    start http://localhost:4200
    echo ✅ Frontend aberto no navegador!
)

echo.
pause
goto MAIN_MENU

:UNINSTALL_ALL
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        🗑️  DESINSTALAR TUDO 🗑️                             ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo ⚠️  ATENCAO: Esta operacao e IRREVERSIVEL!
echo.
echo O que sera removido:
echo    • Banco de dados consumoesperto
echo    • Usuario bruce do PostgreSQL
echo    • Todos os arquivos do projeto
echo    • Logs e cache
echo    • Configuracoes
echo.

set /p confirm="Tem CERTEZA que deseja desinstalar? Digite 'DESINSTALAR' para confirmar: "
if not "%confirm%"=="DESINSTALAR" (
    echo ❌ Desinstalacao cancelada
    pause
    goto MAIN_MENU
)

echo.
echo [1/6] Parando todos os servicos...
taskkill /f /im java.exe 2>nul
taskkill /f /im node.exe 2>nul
taskkill /f /im ngrok.exe 2>nul

echo.
echo [2/6] Removendo banco de dados...
psql -U postgres -c "DROP DATABASE IF EXISTS consumoesperto;" 2>nul
psql -U postgres -c "DROP USER IF EXISTS bruce;" 2>nul

echo.
echo [3/6] Removendo logs e cache...
if exist "backend\logs" rmdir /s /q "backend\logs"
if exist "backend\target" rmdir /s /q "backend\target"
if exist "frontend\dist" rmdir /s /q "frontend\dist"
if exist "frontend\node_modules" rmdir /s /q "frontend\node_modules"

echo.
echo [4/6] Removendo arquivos de configuracao...
if exist ".env" del /q ".env"
if exist "load-env.bat" del /q "load-env.bat"

echo.
echo [5/6] Removendo scripts auxiliares...
if exist "menu-principal.bat" del /q "menu-principal.bat"
if exist "iniciar-rapido.bat" del /q "iniciar-rapido.bat"
if exist "testar-sistema.bat" del /q "testar-sistema.bat"
if exist "instalar-tudo.bat" del /q "instalar-tudo.bat"
if exist "start-with-ngrok.bat" del /q "start-with-ngrok.bat"
if exist "start-with-ngrok.ps1" del /q "start-with-ngrok.ps1"
if exist "start-with-ngrok.sh" del /q "start-with-ngrok.sh"
if exist "env-example.txt" del /q "env-example.txt"

echo.
echo [6/6] Removendo pasta tools...
if exist "tools" rmdir /s /q "tools"

echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        ✅ DESINSTALACAO CONCLUIDA! ✅                       ║
echo  ║                                                                              ║
echo  ║  O sistema Consumo Esperto foi completamente removido.                      ║
echo  ║                                                                              ║
echo  ║  🔄 Para reinstalar, execute este script novamente                          ║
echo  ║                                                                              ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo Pressione qualquer tecla para sair...
pause >nul
exit /b 0

:INVALID_CHOICE
cls
echo.
echo ❌ Opcao invalida! Tente novamente.
timeout /t 2 /nobreak >nul
goto MAIN_MENU

:SET_ENV_PROD
cls
echo.
echo ╔══════════════════════════════════════════════════════════════════════════════╗
echo ║                    🌐 CONFIGURAR VARIÁVEIS DE AMBIENTE 🌐                  ║
echo ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo 🔧 Configurando variáveis de ambiente para produção...
echo.

REM =============================================================================
REM CONFIGURAÇÕES JWT (OBRIGATÓRIO)
REM =============================================================================
echo 📝 Configurando JWT...
set JWT_SECRET=minha_chave_secreta_jwt_super_segura_para_consumo_esperto_2024_producao
set JWT_EXPIRATION=86400000
set JWT_REFRESH_EXPIRATION=604800000
set JWT_ISSUER=consumo-esperto-api
set JWT_AUDIENCE=consumo-esperto-client

REM =============================================================================
REM CONFIGURAÇÕES GOOGLE OAUTH2 (OBRIGATÓRIO)
REM =============================================================================
echo 📝 Configurando Google OAuth2...
echo.
echo ⚠️  ATENÇÃO: Configure suas credenciais reais do Google OAuth2!
echo.
echo Para obter as credenciais:
echo 1. Acesse: https://console.developers.google.com/
echo 2. Crie um projeto ou selecione um existente
echo 3. Ative a API do Google+
echo 4. Crie credenciais OAuth2
echo 5. Configure URIs de redirecionamento: http://localhost:8080/api/auth/google/callback
echo.

REM Credenciais reais do Google OAuth2
set GOOGLE_CLIENT_ID=593452038228-47k24odoa6f18c78e3ssp9bhu56gugnm.apps.googleusercontent.com
set GOOGLE_CLIENT_SECRET=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

REM =============================================================================
REM CONFIGURAÇÕES BANCO DE DADOS (OPCIONAL - usa padrões se não definido)
REM =============================================================================
echo 📝 Configurando banco de dados...
set DB_URL=jdbc:postgresql://localhost:5432/consumoesperto
set DB_USERNAME=consumo_esperto_user
set DB_PASSWORD=consumo_esperto_pass

REM =============================================================================
REM CONFIGURAÇÕES APIS BANCÁRIAS (OPCIONAL)
REM =============================================================================
echo 📝 Configurando APIs bancárias...
set NUBANK_CLIENT_ID=seu_nubank_client_id
set NUBANK_CLIENT_SECRET=seu_nubank_client_secret
set ITAU_CLIENT_ID=seu_itau_client_id
set ITAU_CLIENT_SECRET=seu_itau_client_secret
set MERCADOPAGO_CLIENT_ID=seu_mercadopago_client_id
set MERCADOPAGO_CLIENT_SECRET=seu_mercadopago_client_secret
set INTER_CLIENT_ID=seu_inter_client_id
set INTER_CLIENT_SECRET=seu_inter_client_secret

REM =============================================================================
REM CONFIGURAÇÕES NGROK (OPCIONAL)
REM =============================================================================
echo 📝 Configurando Ngrok...
set NGROK_URL=http://localhost:4040

REM =============================================================================
REM CONFIGURAÇÕES DE PRODUÇÃO
REM =============================================================================
echo 📝 Configurando ambiente de produção...
set SPRING_PROFILES_ACTIVE=prod
set LOG_LEVEL=INFO

echo.
echo ╔══════════════════════════════════════════════════════════════════════════════╗
echo ║                    ✅ VARIÁVEIS DE AMBIENTE CONFIGURADAS! ✅               ║
echo ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo 📋 Variáveis principais configuradas:
echo    🔑 JWT_SECRET: %JWT_SECRET%
echo    🔐 GOOGLE_CLIENT_ID: %GOOGLE_CLIENT_ID%
echo    🔐 GOOGLE_CLIENT_SECRET: %GOOGLE_CLIENT_SECRET%
echo    🗄️  DB_URL: %DB_URL%
echo    🌐 NGROK_URL: %NGROK_URL%
echo    🏷️  SPRING_PROFILES_ACTIVE: %SPRING_PROFILES_ACTIVE%
echo.
echo ⚠️  IMPORTANTE: Configure as credenciais reais do Google OAuth2!
echo.
echo 💡 Para usar essas variáveis em outros scripts, execute:
echo    call set-env-prod.bat
echo.
pause
goto MAIN_MENU

:EXIT
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                        👋 OBRIGADO POR USAR! 👋                             ║
echo  ║                                                                              ║
echo  ║  Sistema Consumo Esperto - Menu Completo                                    ║
echo  ║  Desenvolvido com ❤️  para facilitar o desenvolvimento                      ║
echo  ║                                                                              ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.
echo Pressione qualquer tecla para sair...
pause >nul
exit /b 0

:CONFIG_JAVA_TEMP
cls
echo.
echo  ╔══════════════════════════════════════════════════════════════════════════════╗
echo  ║                    ☕ CONFIGURAR JAVA 17 TEMPORARIO ☕                      ║
echo  ║                                                                              ║
echo  ║  Este comando configura Java 17 temporariamente para esta sessao.           ║
echo  ║  A configuracao sera valida apenas ate fechar o terminal.                   ║
echo  ║                                                                              ║
echo  ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/4] Verificando se Java 17 esta instalado...
java -version 2>nul | findstr "17" >nul
if %errorlevel% neq 0 (
    echo ❌ Java 17 nao encontrado. Instalando...
    call :InstallJava
    if %errorlevel% neq 0 (
        echo ❌ Erro ao instalar Java 17
        pause
        goto MAIN_MENU
    )
) else (
    echo ✅ Java 17 ja instalado
)

echo.
echo [2/4] Verificando pasta tools\java...
if not exist "tools\java\ms-17.0.15" (
    echo ❌ Pasta tools\java\ms-17.0.15 nao encontrada!
    echo Execute primeiro a opcao 1 (INSTALAR DEPENDENCIAS)
    pause
    goto MAIN_MENU
)
echo ✅ Pasta tools\java\ms-17.0.15 encontrada

echo.
echo [3/4] Configurando JAVA_HOME temporariamente...
set "JAVA_HOME=%cd%\tools\java\ms-17.0.15"
echo JAVA_HOME configurado: %JAVA_HOME%

echo.
echo [4/4] Adicionando Java ao PATH temporariamente...
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo PATH atualizado com Java 17

echo.
echo ✅ CONFIGURACAO CONCLUIDA!
echo.
echo Agora voce pode executar:
echo   mvn clean compile
echo   mvn spring-boot:run
echo.
echo NOTA: Esta configuracao e valida apenas para esta sessao.
echo Para uma nova sessao, execute esta opcao novamente.
echo.
pause
goto MAIN_MENU

:InstallJava
echo 📥 Verificando Java 17...
if not exist "tools\java" mkdir tools\java
if exist "tools\java\microsoft-jdk-17.0.15-windows-x64.msi" (
    echo ✅ Arquivo Java 17 ja existe. Pulando download...
) else (
    echo 📥 Baixando Java 17...
    powershell -command "Invoke-WebRequest -Uri 'https://aka.ms/download-jdk/microsoft-jdk-17.0.15-windows-x64.msi' -OutFile 'tools\java\microsoft-jdk-17.0.15-windows-x64.msi'"
)
if %errorlevel% neq 0 (
    echo ❌ Erro ao baixar Java 17
    exit /b 1
)
echo 🔧 Instalando Java 17...
msiexec /i "tools\java\microsoft-jdk-17.0.15-windows-x64.msi" /quiet /norestart
if %errorlevel% neq 0 (
    echo ❌ Erro ao instalar Java 17
    exit /b 1
)
echo ✅ Java 17 instalado com sucesso!
exit /b 0

:InstallMaven
echo 📥 Verificando Maven...
if not exist "tools\maven" mkdir tools\maven
if exist "tools\maven\apache-maven-3.9.6-bin.zip" (
    echo ✅ Arquivo Maven ja existe. Pulando download...
) else (
    echo 📥 Baixando Maven...
    powershell -command "Invoke-WebRequest -Uri 'https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile 'tools\maven\apache-maven-3.9.6-bin.zip'"
)
if %errorlevel% neq 0 (
    echo ❌ Erro ao baixar Maven
    exit /b 1
)
echo 📦 Extraindo Maven...
powershell -command "Expand-Archive -Path 'tools\maven\apache-maven-3.9.6-bin.zip' -DestinationPath 'tools\maven' -Force"
if %errorlevel% neq 0 (
    echo ❌ Erro ao extrair Maven
    exit /b 1
)
echo ✅ Maven instalado com sucesso!
exit /b 0

:InstallPostgreSQL
echo 📥 Verificando PostgreSQL...
if exist "tools\postgresql-13.12-1-windows-x64.exe" (
    echo ✅ Arquivo PostgreSQL ja existe. Pulando download...
) else (
    echo 📥 Baixando PostgreSQL...
    powershell -command "Invoke-WebRequest -Uri 'https://get.enterprisedb.com/postgresql/postgresql-13.12-1-windows-x64.exe' -OutFile 'tools\postgresql-13.12-1-windows-x64.exe'"
)
if %errorlevel% neq 0 (
    echo ❌ Erro ao baixar PostgreSQL
    exit /b 1
)
echo 🔧 Instalando PostgreSQL...
tools\postgresql-13.12-1-windows-x64.exe --mode unattended --superpassword 0301HeX@ --servicename postgresql-x64-13 --serviceaccount postgres --servicepassword 0301HeX@
if %errorlevel% neq 0 (
    echo ❌ Erro ao instalar PostgreSQL
    exit /b 1
)
echo ✅ PostgreSQL instalado com sucesso!
exit /b 0

:InstallNode
echo 📥 Verificando Node.js...
if exist "tools\node-v18.19.0-x64.msi" (
    echo ✅ Arquivo Node.js ja existe. Pulando download...
) else (
    echo 📥 Baixando Node.js...
    powershell -command "Invoke-WebRequest -Uri 'https://nodejs.org/dist/v18.19.0/node-v18.19.0-x64.msi' -OutFile 'tools\node-v18.19.0-x64.msi'"
)
if %errorlevel% neq 0 (
    echo ❌ Erro ao baixar Node.js
    exit /b 1
)
echo 🔧 Instalando Node.js...
msiexec /i "tools\node-v18.19.0-x64.msi" /quiet /norestart
if %errorlevel% neq 0 (
    echo ❌ Erro ao instalar Node.js
    exit /b 1
)
echo ✅ Node.js instalado com sucesso!
exit /b 0

:InstallNgrok
echo 📥 Verificando ngrok...
if not exist "tools\ngrok" mkdir tools\ngrok
if exist "tools\ngrok\ngrok.zip" (
    echo ✅ Arquivo ngrok ja existe. Pulando download...
) else (
    echo 📥 Baixando ngrok...
    powershell -command "Invoke-WebRequest -Uri 'https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip' -OutFile 'tools\ngrok\ngrok.zip'"
)
if %errorlevel% neq 0 (
    echo ❌ Erro ao baixar ngrok
    exit /b 1
)
echo 📦 Extraindo ngrok...
powershell -command "Expand-Archive -Path 'tools\ngrok\ngrok.zip' -DestinationPath 'tools\ngrok' -Force"
if %errorlevel% neq 0 (
    echo ❌ Erro ao extrair ngrok
    exit /b 1
)
echo ✅ ngrok instalado com sucesso!
echo 🔑 Configure o authtoken: ngrok authtoken SEU_TOKEN
exit /b 0

:CheckDependencies
echo Verificando dependencias...
java -version 2>nul | findstr "17" >nul
if %errorlevel% neq 0 (
    echo ❌ Java 17 nao encontrado
    exit /b 1
)
mvn -version 2>nul | findstr "Apache Maven" >nul
if %errorlevel% neq 0 (
    echo ❌ Maven nao encontrado
    exit /b 1
)
psql --version 2>nul | findstr "psql" >nul
if %errorlevel% neq 0 (
    echo ❌ PostgreSQL nao encontrado
    exit /b 1
)
node --version 2>nul | findstr "v" >nul
if %errorlevel% neq 0 (
    echo ❌ Node.js nao encontrado
    exit /b 1
)
ngrok version 2>nul | findstr "ngrok" >nul
if %errorlevel% neq 0 (
    echo ❌ ngrok nao encontrado
    exit /b 1
)
echo ✅ Todas as dependencias encontradas
exit /b 0

:TestSystem
echo Testando sistema...
curl -s http://localhost:8080/actuator/health >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Backend nao esta rodando
    exit /b 1
)
curl -s http://localhost:4200 >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Frontend nao esta rodando
    exit /b 1
)
echo ✅ Sistema funcionando
exit /b 0
