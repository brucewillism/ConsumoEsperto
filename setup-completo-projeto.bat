@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ========================================
::    SETUP COMPLETO - CONSUMO ESPERTO
::    AMBIENTE LOCAL - MENU INTERATIVO
:: ========================================

:menu
cls
echo ========================================
echo    CONSUMO ESPERTO - SETUP COMPLETO
echo    AMBIENTE LOCAL - MENU INTERATIVO
echo ========================================
echo.
echo Escolha uma opcao:
echo.
echo 1. Instalar Java JDK 17
echo 2. Instalar Maven 3.9.6
echo 3. Instalar Node.js 20
echo 4. Instalar Oracle 21c + Banco
echo 5. Fazer TUDO (1+2+3+4)
echo 6. Verificar Status
echo 7. Executar Backend
echo 8. Executar Frontend
echo 9. Configurar Oracle (Docker)
echo 0. Sair
echo.
set /p opcao="Digite sua opcao: "

if "%opcao%"=="1" goto instalar_java
if "%opcao%"=="2" goto instalar_maven
if "%opcao%"=="3" goto instalar_node
if "%opcao%"=="4" goto instalar_oracle
if "%opcao%"=="5" goto fazer_tudo
if "%opcao%"=="6" goto verificar_status
if "%opcao%"=="7" goto executar_backend
if "%opcao%"=="8" goto executar_frontend
if "%opcao%"=="9" goto configurar_oracle_docker
if "%opcao%"=="0" goto sair

echo Opcao invalida!
timeout /t 2 /nobreak >nul
goto menu

:: ========================================
:: 1. INSTALAR JAVA JDK 17
:: ========================================
:instalar_java
cls
echo ========================================
echo    INSTALANDO JAVA JDK 17
echo ========================================
echo.

:: Obter nome do usuario logado
for /f "tokens=2 delims==" %%a in ('wmic computersystem get username /value') do set "CURRENT_USER=%%a"
if "!CURRENT_USER!"=="" set "CURRENT_USER=%USERNAME%"

echo Usuario logado: !CURRENT_USER!
echo.

:: Criar estrutura de diretorios
if not exist "tools" mkdir tools
if not exist "tools\java" mkdir tools\java
if not exist "tools\scripts" mkdir tools\scripts

echo [1/4] Verificando se Java 17 ja esta instalado...
if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    echo ✅ Java JDK 17 ja esta instalado localmente!
    set "JAVA_HOME_LOCAL=%CD%\tools\java\jdk-17.0.2"
    echo Localizacao: !JAVA_HOME_LOCAL!
) else (
    echo ❌ Java JDK 17 nao encontrado. Baixando...
    echo.
    
    echo [2/4] Baixando OpenJDK 17.0.2...
    if not exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
        echo Download em andamento... (pode demorar alguns minutos)
        powershell -Command "& {Invoke-WebRequest -Uri 'https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip' -OutFile '%TEMP%\openjdk-17.0.2_windows-x64_bin.zip'}"
    )
    
    if exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
        echo ✅ Download concluido!
        echo.
        echo [3/4] Extraindo JDK para pasta local...
        powershell -Command "& {Expand-Archive -Path '%TEMP%\openjdk-17.0.2_windows-x64_bin.zip' -DestinationPath 'tools\java' -Force}"
        
        :: Renomear pasta para padrao
        if exist "tools\java\jdk-17.0.2" (
            echo ✅ Java JDK 17.0.2 instalado localmente!
        ) else (
            echo Renomeando pasta do JDK...
            for /d %%i in ("tools\java\jdk-17*") do (
                ren "%%i" "jdk-17.0.2"
            )
        )
        
        set "JAVA_HOME_LOCAL=%CD%\tools\java\jdk-17.0.2"
        echo Localizacao: !JAVA_HOME_LOCAL!
    ) else (
        echo ❌ ERRO: Falha ao baixar Java JDK 17
        echo Verifique sua conexao com a internet e tente novamente
        pause
        goto menu
    )
)

echo.
echo [4/4] Verificando se Java 17 esta funcionando...
"!JAVA_HOME_LOCAL!\bin\java.exe" -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ERRO: Java 17 local nao esta funcionando!
    pause
    goto menu
) else (
    echo ✅ Java 17 local funcionando perfeitamente!
    "!JAVA_HOME_LOCAL!\bin\java.exe" -version
)

echo.
echo ========================================
echo    JAVA JDK 17 INSTALADO COM SUCESSO!
echo ========================================
echo.
echo Localizacao: !JAVA_HOME_LOCAL!
echo.
pause
goto menu

:: ========================================
:: 2. INSTALAR MAVEN 3.9.6
:: ========================================
:instalar_maven
cls
echo ========================================
echo    INSTALANDO MAVEN 3.9.6
echo ========================================
echo.

:: Criar estrutura de diretorios
if not exist "tools" mkdir tools
if not exist "tools\maven" mkdir tools\maven

echo [1/4] Verificando se Maven ja esta instalado...
if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    echo ✅ Maven 3.9.6 ja esta instalado localmente!
    set "MAVEN_HOME_LOCAL=%CD%\tools\maven\apache-maven-3.9.6"
    echo Localizacao: !MAVEN_HOME_LOCAL!
) else (
    echo ❌ Maven nao encontrado. Baixando...
    echo.
    
    echo [2/4] Baixando Maven 3.9.6...
    if not exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
        echo Download em andamento... (pode demorar alguns minutos)
        powershell -Command "& {Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\apache-maven-3.9.6-bin.zip'}"
    )
    
    if exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
        echo ✅ Download concluido!
        echo.
        echo [3/4] Extraindo Maven para pasta local...
        powershell -Command "& {Expand-Archive -Path '%TEMP%\apache-maven-3.9.6-bin.zip' -DestinationPath 'tools\maven' -Force}"
        
        set "MAVEN_HOME_LOCAL=%CD%\tools\maven\apache-maven-3.9.6"
        echo Localizacao: !MAVEN_HOME_LOCAL!
    ) else (
        echo ❌ ERRO: Falha ao baixar Maven
        echo Verifique sua conexao com a internet e tente novamente
        pause
        goto menu
    )
)

echo.
echo [4/4] Verificando se Maven esta funcionando...
"!MAVEN_HOME_LOCAL!\bin\mvn.cmd" -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ERRO: Maven local nao esta funcionando!
    pause
    goto menu
) else (
    echo ✅ Maven local funcionando perfeitamente!
    "!MAVEN_HOME_LOCAL!\bin\mvn.cmd" -version
)

echo.
echo ========================================
echo    MAVEN 3.9.6 INSTALADO COM SUCESSO!
echo ========================================
echo.
echo Localizacao: !MAVEN_HOME_LOCAL!
echo.
pause
goto menu

:: ========================================
:: 3. INSTALAR NODE.JS 20
:: ========================================
:instalar_node
cls
echo ========================================
echo    INSTALANDO NODE.JS 20
echo ========================================
echo.

:: Criar estrutura de diretorios
if not exist "tools" mkdir tools
if not exist "tools\node" mkdir tools\node

echo [1/4] Verificando se Node.js ja esta instalado...
if exist "tools\node\node-v20.11.0-win-x64\node.exe" (
    echo ✅ Node.js 20 ja esta instalado localmente!
    set "NODE_HOME_LOCAL=%CD%\tools\node\node-v20.11.0-win-x64"
    echo Localizacao: !NODE_HOME_LOCAL!
) else (
    echo ❌ Node.js nao encontrado. Baixando...
    echo.
    
    echo [2/4] Baixando Node.js 20.11.0...
    if not exist "%TEMP%\node-v20.11.0-win-x64.zip" (
        echo Download em andamento... (pode demorar alguns minutos)
        powershell -Command "& {Invoke-WebRequest -Uri 'https://nodejs.org/dist/v20.11.0/node-v20.11.0-win-x64.zip' -OutFile '%TEMP%\node-v20.11.0-win-x64.zip'}"
    )
    
    if exist "%TEMP%\node-v20.11.0-win-x64.zip" (
        echo ✅ Download concluido!
        echo.
        echo [3/4] Extraindo Node.js para pasta local...
        powershell -Command "& {Expand-Archive -Path '%TEMP%\node-v20.11.0-win-x64.zip' -DestinationPath 'tools\node' -Force}"
        
        set "NODE_HOME_LOCAL=%CD%\tools\node\node-v20.11.0-win-x64"
        echo Localizacao: !NODE_HOME_LOCAL!
    ) else (
        echo ❌ ERRO: Falha ao baixar Node.js
        echo Verifique sua conexao com a internet e tente novamente
        pause
        goto menu
    )
)

echo.
echo [4/4] Verificando se Node.js esta funcionando...
"!NODE_HOME_LOCAL!\node.exe" -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ERRO: Node.js local nao esta funcionando!
    pause
    goto menu
) else (
    echo ✅ Node.js local funcionando perfeitamente!
    "!NODE_HOME_LOCAL!\node.exe" -version
    "!NODE_HOME_LOCAL!\npm.cmd" -version
)

echo.
echo ========================================
echo    NODE.JS 20 INSTALADO COM SUCESSO!
echo ========================================
echo.
echo Localizacao: !NODE_HOME_LOCAL!
echo.
pause
goto menu

:: ========================================
:: 4. INSTALAR ORACLE 21C + BANCO
:: ========================================
:instalar_oracle
cls
echo ========================================
echo    INSTALANDO ORACLE 21C + BANCO
echo ========================================
echo.

echo [1/5] Verificando se Docker esta disponivel...
docker version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker nao esta rodando ou nao esta instalado!
    echo.
    echo Para resolver:
    echo 1. Instale Docker Desktop
    echo 2. Inicie o Docker Desktop
    echo 3. Execute esta opcao novamente
    echo.
    pause
    goto menu
) else (
    echo ✅ Docker esta funcionando!
)

echo.
echo [2/5] Verificando se Oracle ja esta rodando...
docker ps | findstr "oracle" >nul
if %errorlevel% equ 0 (
    echo ✅ Oracle ja esta rodando no Docker!
) else (
    echo ❌ Oracle nao esta rodando. Iniciando...
    echo.
    echo [3/5] Iniciando Oracle no Docker...
    cd backend
    docker-compose up -d oracle
    cd ..
    
    echo Aguardando Oracle inicializar... (pode demorar 2-3 minutos)
    timeout /t 30 /nobreak >nul
    
    echo [4/5] Verificando se Oracle iniciou...
    docker ps | findstr "oracle" >nul
    if %errorlevel% equ 0 (
        echo ✅ Oracle iniciado com sucesso!
    ) else (
        echo ❌ Falha ao iniciar Oracle no Docker
        echo Verifique os logs: docker-compose logs oracle
        pause
        goto menu
    )
)

echo.
echo [5/5] Criando banco e usuario automaticamente...
echo Aguardando banco estar disponivel...
timeout /t 10 /nobreak >nul

:: Verificar se o usuario ja existe
echo exit | sqlplus -s !CURRENT_USER!/admin123@localhost:1521/XE >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Usuario !CURRENT_USER! ja existe no banco!
) else (
    echo Criando usuario !CURRENT_USER! no banco...
    
    :: Criar arquivo SQL temporario
    echo @echo off > "%TEMP%\setup_oracle.sql"
    echo SET LINESIZE 100 >> "%TEMP%\setup_oracle.sql"
    echo SET PAGESIZE 50 >> "%TEMP%\setup_oracle.sql"
    echo SET FEEDBACK ON >> "%TEMP%\setup_oracle.sql"
    echo SET VERIFY ON >> "%TEMP%\setup_oracle.sql"
    echo. >> "%TEMP%\setup_oracle.sql"
    echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
    echo PROMPT CONECTANDO AO ORACLE COMO SYSTEM... >> "%TEMP%\setup_oracle.sql"
    echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
    echo CONNECT system/oracle123@localhost:1521/XE >> "%TEMP%\setup_oracle.sql"
    echo. >> "%TEMP%\setup_oracle.sql"
    echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
    echo PROMPT CRIANDO TABLESPACE E USUARIO... >> "%TEMP%\setup_oracle.sql"
    echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
    echo CREATE TABLESPACE consumo_esperto_data >> "%TEMP%\setup_oracle.sql"
    echo DATAFILE 'consumo_esperto_data.dbf' >> "%TEMP%\setup_oracle.sql"
    echo SIZE 100M >> "%TEMP%\setup_oracle.sql"
    echo AUTOEXTEND ON NEXT 10M; >> "%TEMP%\setup_oracle.sql"
    echo. >> "%TEMP%\setup_oracle.sql"
    echo CREATE USER !CURRENT_USER! IDENTIFIED BY admin123 >> "%TEMP%\setup_oracle.sql"
    echo DEFAULT TABLESPACE consumo_esperto_data >> "%TEMP%\setup_oracle.sql"
    echo QUOTA UNLIMITED ON consumo_esperto_data; >> "%TEMP%\setup_oracle.sql"
    echo. >> "%TEMP%\setup_oracle.sql"
    echo GRANT CONNECT, RESOURCE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo GRANT CREATE SESSION TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo GRANT CREATE TABLE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo GRANT CREATE SEQUENCE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo GRANT CREATE VIEW TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo GRANT CREATE PROCEDURE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo GRANT CREATE TRIGGER TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
    echo. >> "%TEMP%\setup_oracle.sql"
    echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
    echo PROMPT USUARIO CRIADO COM SUCESSO! >> "%TEMP%\setup_oracle.sql"
    echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
    echo PROMPT. >> "%TEMP%\setup_oracle.sql"
    echo PROMPT Detalhes da conexao: >> "%TEMP%\setup_oracle.sql"
    echo PROMPT - Usuario: !CURRENT_USER! >> "%TEMP%\setup_oracle.sql"
    echo PROMPT - Senha: admin123 >> "%TEMP%\setup_oracle.sql"
    echo PROMPT - Host: localhost >> "%TEMP%\setup_oracle.sql"
    echo PROMPT - Porta: 1521 >> "%TEMP%\setup_oracle.sql"
    echo PROMPT - Service: XE >> "%TEMP%\setup_oracle.sql"
    echo PROMPT. >> "%TEMP%\setup_oracle.sql"
    echo PROMPT Pressione Enter para sair... >> "%TEMP%\setup_oracle.sql"
    echo PAUSE >> "%TEMP%\setup_oracle.sql"
    echo EXIT >> "%TEMP%\setup_oracle.sql"
    
    echo NOTA: Uma janela do SQL*Plus sera aberta.
    echo Execute o script para criar o usuario.
    echo.
    pause
    
    sqlplus /nolog @"%TEMP%\setup_oracle.sql"
    
    :: Limpar arquivo temporario
    del "%TEMP%\setup_oracle.sql"
)

echo.
echo ========================================
echo    ORACLE 21C + BANCO CONFIGURADO!
echo ========================================
echo.
echo Credenciais do banco:
echo - Usuario: !CURRENT_USER!
echo - Senha: admin123
echo - Host: localhost
echo - Porta: 1521
echo - Service: XE
echo - Tablespace: consumo_esperto_data
echo.
pause
goto menu

:: ========================================
:: 5. FAZER TUDO (1+2+3+4)
:: ========================================
:fazer_tudo
cls
echo ========================================
echo    FAZENDO TUDO AUTOMATICAMENTE
echo ========================================
echo.
echo Este processo ira:
echo 1. Instalar Java JDK 17
echo 2. Instalar Maven 3.9.6
echo 3. Instalar Node.js 20
echo 4. Instalar Oracle 21c + Banco
echo.
echo Tempo estimado: 10-15 minutos
echo.
set /p confirm="Continuar? (S/N): "
if /i not "%confirm%"=="S" goto menu

echo.
echo ========================================
echo    INICIANDO INSTALACAO COMPLETA...
echo ========================================
echo.

:: Instalar Java
echo [1/4] Instalando Java JDK 17...
call :instalar_java_silencioso
if %errorlevel% neq 0 (
    echo ❌ Falha na instalacao do Java
    pause
    goto menu
)
echo ✅ Java instalado com sucesso!
echo.

:: Instalar Maven
echo [2/4] Instalando Maven 3.9.6...
call :instalar_maven_silencioso
if %errorlevel% neq 0 (
    echo ❌ Falha na instalacao do Maven
    pause
    goto menu
)
echo ✅ Maven instalado com sucesso!
echo.

:: Instalar Node.js
echo [3/4] Instalando Node.js 20...
call :instalar_node_silencioso
if %errorlevel% neq 0 (
    echo ❌ Falha na instalacao do Node.js
    pause
    goto menu
)
echo ✅ Node.js instalado com sucesso!
echo.

:: Instalar Oracle
echo [4/4] Instalando Oracle 21c + Banco...
call :instalar_oracle_silencioso
if %errorlevel% neq 0 (
    echo ❌ Falha na instalacao do Oracle
    pause
    goto menu
)
echo ✅ Oracle instalado com sucesso!
echo.

:: Criar scripts de ambiente
echo [EXTRA] Criando scripts de ambiente...
call :criar_scripts_ambiente

echo.
echo ========================================
echo    INSTALACAO COMPLETA FINALIZADA!
echo ========================================
echo.
echo Tudo foi instalado com sucesso:
echo ✅ Java JDK 17
echo ✅ Maven 3.9.6
echo ✅ Node.js 20
echo ✅ Oracle 21c + Banco
echo ✅ Scripts de ambiente
echo.
echo Para usar:
echo 1. Ativar ambiente: tools\scripts\ativar-ambiente.bat
echo 2. Executar backend: tools\scripts\executar-backend.bat
echo 3. Executar frontend: tools\scripts\executar-frontend.bat
echo.
pause
goto menu

:: ========================================
:: 6. VERIFICAR STATUS
:: ========================================
:verificar_status
cls
echo ========================================
echo    VERIFICANDO STATUS
echo ========================================
echo.

echo Verificando Java 17 local...
if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    echo ✅ Java 17: OK
    echo    Localizacao: tools\java\jdk-17.0.2
) else (
    echo ❌ Java 17: NAO ENCONTRADO
)

echo.
echo Verificando Maven local...
if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    echo ✅ Maven: OK
    echo    Localizacao: tools\maven\apache-maven-3.9.6
) else (
    echo ❌ Maven: NAO ENCONTRADO
)

echo.
echo Verificando Node.js local...
if exist "tools\node\node-v20.11.0-win-x64\node.exe" (
    echo ✅ Node.js: OK
    echo    Localizacao: tools\node\node-v20.11.0-win-x64
) else (
    echo ❌ Node.js: NAO ENCONTRADO
)

echo.
echo Verificando Oracle...
netstat -an | findstr ":1521" >nul
if %errorlevel% equ 0 (
    echo ✅ Oracle: RODANDO (porta 1521)
) else (
    echo ❌ Oracle: NAO RODANDO
)

echo.
echo Verificando scripts de ambiente...
if exist "tools\scripts\ativar-ambiente.bat" (
    echo ✅ Scripts: OK
) else (
    echo ❌ Scripts: NAO ENCONTRADOS
)

echo.
pause
goto menu

:: ========================================
:: 7. EXECUTAR BACKEND
:: ========================================
:executar_backend
cls
echo ========================================
echo    EXECUTANDO BACKEND
echo ========================================
echo.

if not exist "tools\scripts\ativar-ambiente.bat" (
    echo ❌ Scripts de ambiente nao encontrados!
    echo Execute a opcao 5 (Fazer TUDO) primeiro.
    pause
    goto menu
)

echo Ativando ambiente local...
call "tools\scripts\ativar-ambiente.bat"
echo.
echo Executando backend...
cd backend
mvn spring-boot:run -Dspring.profiles.active=dev
cd ..
echo.
pause
goto menu

:: ========================================
:: 8. EXECUTAR FRONTEND
:: ========================================
:executar_frontend
cls
echo ========================================
echo    EXECUTANDO FRONTEND
echo ========================================
echo.

if not exist "tools\scripts\ativar-ambiente.bat" (
    echo ❌ Scripts de ambiente nao encontrados!
    echo Execute a opcao 5 (Fazer TUDO) primeiro.
    pause
    goto menu
)

echo Ativando ambiente local...
call "tools\scripts\ativar-ambiente.bat"
echo.
echo Executando frontend...
cd frontend
ng serve
cd ..
echo.
pause
goto menu

:: ========================================
:: 9. CONFIGURAR ORACLE (DOCKER)
:: ========================================
:configurar_oracle_docker
cls
echo ========================================
echo    CONFIGURANDO ORACLE (DOCKER)
echo ========================================
echo.

echo Verificando se Docker esta disponivel...
docker version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker nao esta rodando ou nao esta instalado!
    echo.
    echo Para resolver:
    echo 1. Instale Docker Desktop
    echo 2. Inicie o Docker Desktop
    echo 3. Execute esta opcao novamente
    echo.
    pause
    goto menu
)

echo ✅ Docker esta funcionando!
echo.
echo Iniciando Oracle no Docker...
cd backend
docker-compose up -d oracle
cd ..

echo Aguardando Oracle inicializar... (pode demorar 2-3 minutos)
timeout /t 30 /nobreak >nul

echo Verificando se Oracle iniciou...
docker ps | findstr "oracle" >nul
if %errorlevel% equ 0 (
    echo ✅ Oracle iniciado com sucesso!
    echo.
    echo Para criar usuario no banco:
    echo Execute a opcao 4 (Instalar Oracle 21c + Banco)
    echo.
) else (
    echo ❌ Falha ao iniciar Oracle no Docker
    echo Verifique os logs: docker-compose logs oracle
)

pause
goto menu

:: ========================================
:: FUNCOES AUXILIARES
:: ========================================

:instalar_java_silencioso
if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    set "JAVA_HOME_LOCAL=%CD%\tools\java\jdk-17.0.2"
    exit /b 0
)

if not exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
    powershell -Command "& {Invoke-WebRequest -Uri 'https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip' -OutFile '%TEMP%\openjdk-17.0.2_windows-x64_bin.zip'}"
)

if exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
    powershell -Command "& {Expand-Archive -Path '%TEMP%\openjdk-17.0.2_windows-x64_bin.zip' -DestinationPath 'tools\java' -Force}"
    
    if exist "tools\java\jdk-17.0.2" (
        set "JAVA_HOME_LOCAL=%CD%\tools\java\jdk-17.0.2"
        exit /b 0
    ) else (
        for /d %%i in ("tools\java\jdk-17*") do (
            ren "%%i" "jdk-17.0.2"
        )
        set "JAVA_HOME_LOCAL=%CD%\tools\java\jdk-17.0.2"
        exit /b 0
    )
) else (
    exit /b 1
)

:instalar_maven_silencioso
if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MAVEN_HOME_LOCAL=%CD%\tools\maven\apache-maven-3.9.6"
    exit /b 0
)

if not exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
    powershell -Command "& {Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\apache-maven-3.9.6-bin.zip'}"
)

if exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
    powershell -Command "& {Expand-Archive -Path '%TEMP%\apache-maven-3.9.6-bin.zip' -DestinationPath 'tools\maven' -Force}"
    set "MAVEN_HOME_LOCAL=%CD%\tools\maven\apache-maven-3.9.6"
    exit /b 0
) else (
    exit /b 1
)

:instalar_node_silencioso
if exist "tools\node\node-v20.11.0-win-x64\node.exe" (
    set "NODE_HOME_LOCAL=%CD%\tools\node\node-v20.11.0-win-x64"
    exit /b 0
)

if not exist "%TEMP%\node-v20.11.0-win-x64.zip" (
    powershell -Command "& {Invoke-WebRequest -Uri 'https://nodejs.org/dist/v20.11.0/node-v20.11.0-win-x64.zip' -OutFile '%TEMP%\node-v20.11.0-win-x64.zip'}"
)

if exist "%TEMP%\node-v20.11.0-win-x64.zip" (
    powershell -Command "& {Expand-Archive -Path '%TEMP%\node-v20.11.0-win-x64.zip' -DestinationPath 'tools\node' -Force}"
    set "NODE_HOME_LOCAL=%CD%\tools\node\node-v20.11.0-win-x64"
    exit /b 0
) else (
    exit /b 1
)

:instalar_oracle_silencioso
docker version >nul 2>&1
if %errorlevel% neq 0 exit /b 1

docker ps | findstr "oracle" >nul
if %errorlevel% equ 0 exit /b 0

cd backend
docker-compose up -d oracle
cd ..

timeout /t 30 /nobreak >nul

docker ps | findstr "oracle" >nul
if %errorlevel% equ 0 exit /b 0
exit /b 1

:criar_scripts_ambiente
if not exist "tools\scripts" mkdir tools\scripts

:: Script para ativar o ambiente
echo @echo off > "tools\scripts\ativar-ambiente.bat"
echo chcp 65001 ^>nul >> "tools\scripts\ativar-ambiente.bat"
echo setlocal enabledelayedexpansion >> "tools\scripts\ativar-ambiente.bat"
echo. >> "tools\scripts\ativar-ambiente.bat"
echo echo ======================================== >> "tools\scripts\ativar-ambiente.bat"
echo echo    ATIVANDO AMBIENTE LOCAL >> "tools\scripts\ativar-ambiente.bat"
echo echo    CONSUMO ESPERTO - JAVA 17 >> "tools\scripts\ativar-ambiente.bat"
echo echo ======================================== >> "tools\scripts\ativar-ambiente.bat"
echo echo. >> "tools\scripts\ativar-ambiente.bat"
echo echo Configurando variaveis de ambiente... >> "tools\scripts\ativar-ambiente.bat"
echo echo. >> "tools\scripts\ativar-ambiente.bat"
echo set "JAVA_HOME=%%~dp0..\java\jdk-17.0.2" >> "tools\scripts\ativar-ambiente.bat"
echo set "MAVEN_HOME=%%~dp0..\maven\apache-maven-3.9.6" >> "tools\scripts\ativar-ambiente.bat"
echo set "NODE_HOME=%%~dp0..\node\node-v20.11.0-win-x64" >> "tools\scripts\ativar-ambiente.bat"
echo. >> "tools\scripts\ativar-ambiente.bat"
echo set "PATH=%%JAVA_HOME%%\bin;%%MAVEN_HOME%%\bin;%%NODE_HOME%%;%%PATH%%" >> "tools\scripts\ativar-ambiente.bat"
echo. >> "tools\scripts\ativar-ambiente.bat"
echo echo ✅ Ambiente ativado! >> "tools\scripts\ativar-ambiente.bat"
echo echo. >> "tools\scripts\ativar-ambiente.bat"
echo echo Java: %%JAVA_HOME%% >> "tools\scripts\ativar-ambiente.bat"
echo echo Maven: %%MAVEN_HOME%% >> "tools\scripts\ativar-ambiente.bat"
echo echo Node: %%NODE_HOME%% >> "tools\scripts\ativar-ambiente.bat"
echo echo. >> "tools\scripts\ativar-ambiente.bat"
echo echo Para usar: >> "tools\scripts\ativar-ambiente.bat"
echo echo   java -version >> "tools\scripts\ativar-ambiente.bat"
echo echo   mvn -version >> "tools\scripts\ativar-ambiente.bat"
echo echo   node -version >> "tools\scripts\ativar-ambiente.bat"
echo echo. >> "tools\scripts\ativar-ambiente.bat"
echo echo Para sair: exit >> "tools\scripts\ativar-ambiente.bat"
echo echo. >> "tools\scripts\ativar-ambiente.bat"
echo cmd /k >> "tools\scripts\ativar-ambiente.bat"

:: Script para executar o backend
echo @echo off > "tools\scripts\executar-backend.bat"
echo chcp 65001 ^>nul >> "tools\scripts\executar-backend.bat"
echo setlocal enabledelayedexpansion >> "tools\scripts\executar-backend.bat"
echo. >> "tools\scripts\executar-backend.bat"
echo echo ======================================== >> "tools\scripts\executar-backend.bat"
echo echo    EXECUTANDO BACKEND >> "tools\scripts\executar-backend.bat"
echo echo    CONSUMO ESPERTO - JAVA 17 >> "tools\scripts\executar-backend.bat"
echo echo ======================================== >> "tools\scripts\executar-backend.bat"
echo echo. >> "tools\scripts\executar-backend.bat"
echo echo Ativando ambiente local... >> "tools\scripts\executar-backend.bat"
echo call "%%~dp0ativar-ambiente.bat" >> "tools\scripts\executar-backend.bat"
echo echo. >> "tools\scripts\executar-backend.bat"
echo echo Executando backend... >> "tools\scripts\executar-backend.bat"
echo cd "%%~dp0..\..\backend" >> "tools\scripts\executar-backend.bat"
echo mvn spring-boot:run -Dspring.profiles.active=dev >> "tools\scripts\executar-backend.bat"

:: Script para executar o frontend
echo @echo off > "tools\scripts\executar-frontend.bat"
echo chcp 65001 ^>nul >> "tools\scripts\executar-frontend.bat"
echo setlocal enabledelayedexpansion >> "tools\scripts\executar-frontend.bat"
echo. >> "tools\scripts\executar-frontend.bat"
echo echo ======================================== >> "tools\scripts\executar-frontend.bat"
echo echo    EXECUTANDO FRONTEND >> "tools\scripts\executar-frontend.bat"
echo echo    CONSUMO ESPERTO - NODE.JS 20 >> "tools\scripts\executar-frontend.bat"
echo echo ======================================== >> "tools\scripts\executar-frontend.bat"
echo echo. >> "tools\scripts\executar-frontend.bat"
echo echo Ativando ambiente local... >> "tools\scripts\executar-frontend.bat"
echo call "%%~dp0ativar-ambiente.bat" >> "tools\scripts\executar-frontend.bat"
echo echo. >> "tools\scripts\executar-frontend.bat"
echo echo Executando frontend... >> "tools\scripts\executar-frontend.bat"
echo cd "%%~dp0..\..\frontend" >> "tools\scripts\executar-frontend.bat"
echo ng serve >> "tools\scripts\executar-frontend.bat"

exit /b 0

:: ========================================
:: SAIR
:: ========================================
:sair
echo.
echo Saindo...
exit /b 0
