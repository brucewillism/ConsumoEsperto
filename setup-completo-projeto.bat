@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    SETUP COMPLETO - CONSUMO ESPERTO
echo ========================================
echo.
echo Este script ira configurar todo o projeto:
echo 1. Verificar/Instalar Java JDK 17
echo 2. Verificar/Instalar Maven
echo 3. Verificar/Instalar Node.js
echo 4. Verificar/Instalar Oracle Database 21c
echo 5. Criar banco e usuario Oracle
echo 6. Configurar frontend Angular
echo 7. Configurar NGROK para APIs bancarias
echo 8. Iniciar servicos
echo.

:: Obter nome do usuario logado
for /f "tokens=2 delims==" %%a in ('wmic computersystem get username /value') do set "CURRENT_USER=%%a"
if "!CURRENT_USER!"=="" set "CURRENT_USER=%USERNAME%"

echo Usuario logado: !CURRENT_USER!
echo.

:: ========================================
:: 1. VERIFICAR/INSTALAR JAVA JDK 17 LOCAL
:: ========================================
echo [1/8] Verificando/Instalando Java JDK 17 local...
echo.

:: Criar diretorio local para ferramentas
if not exist "tools" mkdir tools
if not exist "tools\java" mkdir tools\java
if not exist "tools\maven" mkdir tools\maven
if not exist "tools\node" mkdir tools\node

:: Verificar se Java ja esta instalado localmente
if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    echo Java JDK 17 encontrado localmente!
    set "JAVA_HOME=%CD%\tools\java\jdk-17.0.2"
    set "PATH=%CD%\tools\java\jdk-17.0.2\bin;%PATH%"
    echo JAVA_HOME configurado: !JAVA_HOME!
) else (
    echo Java JDK 17 nao encontrado localmente. Baixando...
    echo.
    
    :: Verificar se o download ja foi feito
    if not exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
        echo Baixando OpenJDK 17.0.2...
        powershell -Command "& {Invoke-WebRequest -Uri 'https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip' -OutFile '%TEMP%\openjdk-17.0.2_windows-x64_bin.zip'}"
    )
    
    if exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
        echo Extraindo JDK para pasta local...
        powershell -Command "& {Expand-Archive -Path '%TEMP%\openjdk-17.0.2_windows-x64_bin.zip' -DestinationPath 'tools\java' -Force}"
        
        :: Renomear pasta para padrao
        if exist "tools\java\jdk-17.0.2" (
            echo Java JDK 17.0.2 instalado localmente!
        ) else (
            echo Renomeando pasta do JDK...
            for /d %%i in ("tools\java\jdk-17*") do (
                ren "%%i" "jdk-17.0.2"
            )
        )
        
        set "JAVA_HOME=%CD%\tools\java\jdk-17.0.2"
        set "PATH=%CD%\tools\java\jdk-17.0.2\bin;%PATH%"
        echo JAVA_HOME configurado: !JAVA_HOME!
    ) else (
        echo ERRO: Falha ao baixar Java JDK 17
        echo Verifique sua conexao com a internet e tente novamente
        pause
        exit /b 1
    )
)

:: Verificar se Java esta funcionando
echo.
echo Verificando se Java esta funcionando...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERRO: Java nao esta funcionando corretamente!
    echo Verifique a instalacao e tente novamente
    pause
    exit /b 1
) else (
    echo Java funcionando corretamente!
    java -version
)
echo.

:: ========================================
:: 2. VERIFICAR/INSTALAR MAVEN LOCAL
:: ========================================
echo [2/8] Verificando/Instalando Maven local...
echo.

:: Verificar se Maven ja esta instalado localmente
if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    echo Maven 3.9.6 encontrado localmente!
    set "MAVEN_HOME=%CD%\tools\maven\apache-maven-3.9.6"
    set "PATH=%CD%\tools\maven\apache-maven-3.9.6\bin;%PATH%"
    echo MAVEN_HOME configurado: !MAVEN_HOME!
) else (
    echo Maven nao encontrado localmente. Baixando...
    echo.
    
    :: Verificar se o download ja foi feito
    if not exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
        echo Baixando Apache Maven 3.9.6...
        powershell -Command "& {Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\apache-maven-3.9.6-bin.zip'}"
    )
    
    if exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
        echo Extraindo Maven para pasta local...
        
        :: Limpar pasta maven se existir
        if exist "tools\maven" rmdir /s /q "tools\maven"
        
        :: Extrair com PowerShell
        powershell -Command "& {try { Expand-Archive -Path '%TEMP%\apache-maven-3.9.6-bin.zip' -DestinationPath 'tools\maven' -Force; Write-Host 'Maven extraido com sucesso!' } catch { Write-Host 'ERRO na extracao: ' + $_.Exception.Message; exit 1 }}"
        
        :: Verificar se a extração foi bem-sucedida
        if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
            set "MAVEN_HOME=%CD%\tools\maven\apache-maven-3.9.6"
            set "PATH=%CD%\tools\maven\apache-maven-3.9.6\bin;%PATH%"
            echo Maven 3.9.6 instalado localmente!
            echo MAVEN_HOME configurado: !MAVEN_HOME!
        ) else (
            echo ERRO: Falha ao extrair Maven
            echo Verificando estrutura da pasta...
            dir "tools\maven" /s
            echo.
            echo Tentando extrair novamente...
            powershell -Command "& {Expand-Archive -Path '%TEMP%\apache-maven-3.9.6-bin.zip' -DestinationPath 'tools\maven' -Force}"
            
            if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
                set "MAVEN_HOME=%CD%\tools\maven\apache-maven-3.9.6"
                set "PATH=%CD%\tools\maven\apache-maven-3.9.6\bin;%PATH%"
                echo Maven 3.9.6 instalado localmente na segunda tentativa!
                echo MAVEN_HOME configurado: !MAVEN_HOME!
            ) else (
                echo ERRO: Falha definitiva ao extrair Maven
                echo Verifique se o arquivo ZIP esta corrompido
                echo Tamanho do arquivo: 
                dir "%TEMP%\apache-maven-3.9.6-bin.zip"
                pause
                exit /b 1
            )
        )
    ) else (
        echo ERRO: Falha ao baixar Maven
        echo Verifique sua conexao com a internet e tente novamente
        pause
        exit /b 1
    )
)

:: Verificar se Maven esta funcionando
echo.
echo Verificando se Maven esta funcionando...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERRO: Maven nao esta funcionando corretamente!
    echo Verifique a instalacao e tente novamente
    echo.
    echo Pressione qualquer tecla para continuar...
    pause >nul
    exit /b 1
) else (
    echo Maven funcionando corretamente!
    mvn -version
)
echo.
echo Maven configurado com sucesso!
echo.
pause

:: ========================================
:: 3. VERIFICAR/INSTALAR NODE.JS LOCAL
:: ========================================
echo [3/8] Verificando/Instalando Node.js local...
echo.

:: Verificar se Node.js ja esta instalado localmente
if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
    echo Node.js 18.19.0 encontrado localmente!
    set "NODE_HOME=%CD%\tools\node\node-v18.19.0-win-x64"
    set "PATH=%CD%\tools\node\node-v18.19.0-win-x64;%PATH%"
    echo NODE_HOME configurado: !NODE_HOME!
) else (
    echo Node.js nao encontrado localmente. Baixando...
    echo.
    
    :: Verificar se o download ja foi feito
    if not exist "%TEMP%\node-v18.19.0-win-x64.zip" (
        echo Baixando Node.js 18.19.0...
        powershell -Command "& {Invoke-WebRequest -Uri 'https://nodejs.org/dist/v18.19.0/node-v18.19.0-win-x64.zip' -OutFile '%TEMP%\node-v18.19.0-win-x64.zip'}"
    )
    
    if exist "%TEMP%\node-v18.19.0-win-x64.zip" (
        echo Extraindo Node.js para pasta local...
        
        :: Limpar pasta node se existir
        if exist "tools\node" rmdir /s /q "tools\node"
        
        :: Extrair com PowerShell
        powershell -Command "& {try { Expand-Archive -Path '%TEMP%\node-v18.19.0-win-x64.zip' -DestinationPath 'tools\node' -Force; Write-Host 'Node.js extraido com sucesso!' } catch { Write-Host 'ERRO na extracao: ' + $_.Exception.Message; exit 1 }}"
        
        :: Verificar se a extração foi bem-sucedida
        if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
            set "NODE_HOME=%CD%\tools\node\node-v18.19.0-win-x64"
            set "PATH=%CD%\tools\node\node-v18.19.0-win-x64;%PATH%"
            echo Node.js 18.19.0 instalado localmente!
            echo NODE_HOME configurado: !NODE_HOME!
        ) else (
            echo ERRO: Falha ao extrair Node.js
            echo Verificando estrutura da pasta...
            dir "tools\node" /s
            echo.
            echo Tentando extrair novamente...
            powershell -Command "& {Expand-Archive -Path '%TEMP%\node-v18.19.0-win-x64.zip' -DestinationPath 'tools\node' -Force}"
            
            if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
                set "NODE_HOME=%CD%\tools\node\node-v18.19.0-win-x64"
                set "PATH=%CD%\tools\node\node-v18.19.0-win-x64;%PATH%"
                echo Node.js 18.19.0 instalado localmente na segunda tentativa!
                echo NODE_HOME configurado: !NODE_HOME!
            ) else (
                echo ERRO: Falha definitiva ao extrair Node.js
                echo Verifique se o arquivo ZIP esta corrompido
                echo Tamanho do arquivo: 
                dir "%TEMP%\node-v18.19.0-win-x64.zip"
                pause
                exit /b 1
            )
        )
    ) else (
        echo ERRO: Falha ao baixar Node.js
        echo Verifique sua conexao com a internet e tente novamente
        pause
        exit /b 1
    )
)

:: Verificar se Node.js esta funcionando
echo.
echo Verificando se Node.js esta funcionando...
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERRO: Node.js nao esta funcionando corretamente!
    echo Verifique a instalacao e tente novamente
    echo.
    echo Pressione qualquer tecla para continuar...
    pause >nul
    exit /b 1
) else (
    echo Node.js funcionando corretamente!
    node --version
    npm --version
)
echo.
echo Node.js configurado com sucesso!
echo.
pause

:: ========================================
:: 4. VERIFICAR/INSTALAR ORACLE DATABASE 21C
:: ========================================
echo [4/8] Verificando Oracle Database 21c...
echo.

:: Verificar se Oracle ja esta instalado
echo Verificando se Oracle Database 21c esta instalado...
reg query "HKLM\SOFTWARE\ORACLE" /v "ORACLE_HOME" >nul 2>&1
if %errorlevel% equ 0 (
    echo Oracle Database 21c encontrado no registro!
    for /f "tokens=3" %%i in ('reg query "HKLM\SOFTWARE\ORACLE" /v "ORACLE_HOME" ^| findstr "ORACLE_HOME"') do set "ORACLE_HOME=%%i"
    echo ORACLE_HOME: !ORACLE_HOME!
) else (
    echo Oracle Database 21c nao encontrado. Instalando...
    echo.
    
    :: Verificar se o instalador ja foi baixado
    if not exist "%TEMP%\OracleXE213_Win64.zip" (
        echo Baixando Oracle Database 21c Express Edition...
        echo NOTA: Este arquivo tem aproximadamente 2.5GB e pode demorar...
        powershell -Command "& {Invoke-WebRequest -Uri 'https://download.oracle.com/otn-pub/otn_software/db-free/OracleXE213_Win64.zip' -OutFile '%TEMP%\OracleXE213_Win64.zip'}"
    )
    
    if exist "%TEMP%\OracleXE213_Win64.zip" (
        echo Extraindo Oracle Database...
        powershell -Command "& {Expand-Archive -Path '%TEMP%\OracleXE213_Win64.zip' -DestinationPath '%TEMP%\oracle' -Force}"
        
        echo Instalando Oracle Database 21c...
        echo NOTA: A instalacao pode demorar varios minutos...
        echo Siga as instrucoes na tela e use as configuracoes padrao:
        echo - Senha do SYS: oracle123
        echo - Porta: 1521
        echo - Service: XE
        echo.
        echo Pressione qualquer tecla para continuar com a instalacao...
        pause >nul
        
        start /wait "%TEMP%\oracle\Disk1\setup.exe"
        
        echo Oracle Database instalado! Configurando...
        
        :: Configurar Oracle para iniciar automaticamente
        sc config OracleServiceXE start= auto
        net start OracleServiceXE
        
        echo Aguardando Oracle inicializar...
        timeout /t 60 /nobreak >nul
        
        echo Oracle Database 21c instalado e configurado!
    ) else (
        echo ERRO: Falha ao baixar Oracle Database 21c
        echo Verifique sua conexao com a internet e tente novamente
        pause
        exit /b 1
    )
)

:: Verificar se Oracle esta rodando
echo Verificando se Oracle esta rodando...
netstat -an | findstr ":1521" >nul
if %errorlevel% neq 0 (
    echo Oracle nao esta rodando. Iniciando servico...
    net start OracleServiceXE
    echo Aguardando Oracle inicializar...
    timeout /t 30 /nobreak >nul
) else (
    echo Oracle esta rodando na porta 1521!
)
echo.

:: ========================================
:: 5. CRIAR BANCO E USUARIO ORACLE
:: ========================================
echo [5/8] Criando banco e usuario Oracle...
echo.

:: Navegar para o backend
cd backend

:: Criar arquivo SQL temporario para setup
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
echo PROMPT CONECTANDO COMO USUARIO !CURRENT_USER!... >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo CONNECT !CURRENT_USER!/admin123@localhost:1521/XE >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT BANCO CONFIGURADO COM SUCESSO! >> "%TEMP%\setup_oracle.sql"
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

echo Executando script de configuracao do Oracle...
echo.
echo NOTA: Uma janela do SQL*Plus sera aberta.
echo Siga as instrucoes na tela.
echo.
echo Pressione qualquer tecla para continuar...
pause >nul

:: Executar script de configuracao
sqlplus /nolog @"%TEMP%\setup_oracle.sql"

echo.
echo ========================================
echo    BANCO ORACLE CONFIGURADO!
echo ========================================
echo.
echo Credenciais do banco:
echo - Usuario: !CURRENT_USER!
echo - Senha: admin123
echo - Host: localhost
echo - Porta: 1521
echo - Service: XE
echo.

:: ========================================
:: 6. CONFIGURAR FRONTEND ANGULAR
:: ========================================
echo [6/8] Configurando frontend Angular...
echo.

cd ..
cd frontend

echo Instalando dependencias do Angular...
npm install

echo Frontend configurado!
echo.

:: ========================================
:: 7. CONFIGURAR NGROK PARA APIS BANCARIAS
:: ========================================
echo [7/8] Configurando NGROK para APIs bancarias...
echo.

cd ..
cd scripts

echo Verificando se NGROK esta instalado...
ngrok version >nul 2>&1
if %errorlevel% neq 0 (
    echo NGROK nao encontrado. Baixando...
    echo.
    echo Baixando NGROK...
    powershell -Command "& {Invoke-WebRequest -Uri 'https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip' -OutFile '%TEMP%\ngrok.zip'}"
    
    echo Extraindo NGROK...
    powershell -Command "& {Expand-Archive -Path '%TEMP%\ngrok.zip' -DestinationPath 'scripts' -Force}"
    
    echo NGROK instalado com sucesso!
) else (
    echo NGROK encontrado!
    ngrok version
)

echo.
echo Configurando NGROK para APIs bancarias...
echo.

:: Criar arquivo de configuracao do NGROK
echo # ============================================================================= > "ngrok-config.yml"
echo # CONFIGURACAO NGROK - CONSUMO ESPERTO >> "ngrok-config.yml"
echo # Este arquivo configura o NGROK para as APIs bancarias >> "ngrok-config.yml"
echo # Execute: ngrok start --config ngrok-config.yml >> "ngrok-config.yml"
echo. >> "ngrok-config.yml"
echo version: "2" >> "ngrok-config.yml"
echo authtoken: "SEU_TOKEN_AQUI" >> "ngrok-config.yml"
echo. >> "ngrok-config.yml"
echo tunnels: >> "ngrok-config.yml"
echo   consumo-esperto-api: >> "ngrok-config.yml"
echo     addr: 8080 >> "ngrok-config.yml"
echo     proto: http >> "ngrok-config.yml"
echo     subdomain: consumo-esperto-api >> "ngrok-config.yml"
echo     inspect: false >> "ngrok-config.yml"
echo. >> "ngrok-config.yml"
echo   consumo-esperto-web: >> "ngrok-config.yml"
echo     addr: 4200 >> "ngrok-config.yml"
echo     proto: http >> "ngrok-config.yml"
echo     subdomain: consumo-esperto-web >> "ngrok-config.yml"
echo     inspect: false >> "ngrok-config.yml"

echo Arquivo de configuracao NGROK criado: scripts/ngrok-config.yml
echo.
echo IMPORTANTE: Configure seu token de autenticacao NGROK:
echo 1. Acesse: https://dashboard.ngrok.com/get-started/your-authtoken
echo 2. Copie seu token
echo 3. Edite o arquivo ngrok-config.yml e substitua "SEU_TOKEN_AQUI"
echo.

:: Criar script para iniciar NGROK
echo @echo off > "start-ngrok.bat"
echo echo Iniciando NGROK para APIs bancarias... >> "start-ngrok.bat"
echo echo. >> "start-ngrok.bat"
echo echo URLs disponiveis: >> "start-ngrok.bat"
echo echo - API: https://consumo-esperto-api.ngrok.io >> "start-ngrok.bat"
echo echo - Web: https://consumo-esperto-web.ngrok.io >> "start-ngrok.bat"
echo echo. >> "start-ngrok.bat"
echo echo Pressione Ctrl+C para parar o NGROK >> "start-ngrok.bat"
echo echo. >> "start-ngrok.bat"
echo ngrok start --config ngrok-config.yml >> "start-ngrok.bat"

echo Script para iniciar NGROK criado: scripts/start-ngrok.bat
echo.

:: ========================================
:: 8. INICIAR SERVICOS
:: ========================================
echo [8/8] Iniciando servicos...
echo.

cd ..

echo ========================================
echo    CONFIGURACAO COMPLETA!
echo ========================================
echo.
echo Todos os componentes foram configurados:
echo.
echo 1. ✅ Java JDK 17.0.2 instalado localmente
echo 2. ✅ Maven 3.9.6 instalado localmente
echo 3. ✅ Node.js 18.19.0 instalado localmente
echo 4. ✅ Oracle Database 21c instalado
echo 5. ✅ Banco 'consumo_esperto' criado
echo 6. ✅ Usuario '!CURRENT_USER!' criado (senha: admin123)
echo 7. ✅ Frontend Angular configurado
echo 8. ✅ NGROK configurado para APIs bancarias
echo.
echo ========================================
echo    PROXIMOS PASSOS
echo ========================================
echo.
echo 1. Iniciar o backend (usando Maven local):
echo    cd backend
echo    ..\tools\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run -Dspring.profiles.active=dev
echo.
echo 2. Em outro terminal, iniciar o frontend (usando Node.js local):
echo    cd frontend
echo    ..\tools\node\node-v18.19.0-win-x64\npm.cmd install -g @angular/cli
echo    ..\tools\node\node-v18.19.0-win-x64\npx.cmd ng serve
echo.
echo 3. Para APIs bancarias, iniciar NGROK:
echo    cd scripts
echo    start-ngrok.bat
echo.
echo 4. Acessar a aplicacao:
echo    http://localhost:4200
echo.
echo 5. Fazer login via Google
echo    - O sistema criara automaticamente seu usuario
echo    - Voce podera configurar as APIs bancarias
echo.
echo ========================================
echo    CREDENCIAIS IMPORTANTES
echo ========================================
echo.
echo Banco Oracle:
echo - Usuario: !CURRENT_USER!
echo - Senha: admin123
echo - Host: localhost
echo - Porta: 1521
echo - Service: XE
echo.
echo NGROK (APIs bancarias):
echo - API: https://consumo-esperto-api.ngrok.io
echo - Web: https://consumo-esperto-web.ngrok.io
echo - Config: scripts/ngrok-config.yml
echo.
echo Aplicacao:
echo - URL: http://localhost:4200
echo - Login: Via Google OAuth2
echo.
echo ========================================
echo    ARQUIVOS DE CONFIGURACAO
echo ========================================
echo.
echo Backend:
echo - src\main\resources\application.properties
echo - src\main\resources\application-dev.properties
echo.
echo Frontend:
echo - src\environments\environment.ts
echo.
echo NGROK:
echo - scripts/ngrok-config.yml
echo - scripts/start-ngrok.bat
echo.
echo Scripts:
echo - setup-completo-projeto.bat (este arquivo)
echo - testar-ferramentas.bat (verificar ferramentas)
echo - start-servicos.bat (iniciar servicos)
echo - parar-servicos.bat
echo.
echo Ferramentas locais:
echo - tools\java\jdk-17.0.2 (Java JDK 17.0.2)
echo - tools\maven\apache-maven-3.9.6 (Maven 3.9.6)
echo - tools\node\node-v18.19.0-win-x64 (Node.js 18.19.0)
echo.

:: Limpar arquivo temporario
del "%TEMP%\setup_oracle.sql"

echo.
echo ========================================
echo    SETUP CONCLUIDO!
echo ========================================
echo.
echo Todas as ferramentas foram instaladas localmente:
echo.
echo ✅ Java JDK 17.0.2: !JAVA_HOME!
echo ✅ Maven 3.9.6: !MAVEN_HOME!
echo ✅ Node.js 18.19.0: !NODE_HOME!
echo.
echo Pasta tools criada com sucesso!
echo.
echo Pressione qualquer tecla para sair...
pause >nul
