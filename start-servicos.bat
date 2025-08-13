@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    INICIAR SERVICOS - CONSUMO ESPERTO
echo ========================================
echo.

:: Configurar ferramentas locais
if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    set "JAVA_HOME=%CD%\tools\java\jdk-17.0.2"
    set "PATH=%CD%\tools\java\jdk-17.0.2\bin;%PATH%"
    echo Java JDK 17.0.2 configurado
) else (
    echo ERRO: Java JDK 17.0.2 nao encontrado!
    echo Execute setup-completo-projeto.bat primeiro
    pause
    exit /b 1
)

if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MAVEN_HOME=%CD%\tools\maven\apache-maven-3.9.6"
    set "PATH=%CD%\tools\maven\apache-maven-3.9.6\bin;%PATH%"
    echo Maven 3.9.6 configurado
) else (
    echo ERRO: Maven 3.9.6 nao encontrado!
    echo Execute setup-completo-projeto.bat primeiro
    pause
    exit /b 1
)

if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
    set "NODE_HOME=%CD%\tools\node\node-v18.19.0-win-x64"
    set "PATH=%CD%\tools\node\node-v18.19.0-win-x64;%PATH%"
    echo Node.js 18.19.0 configurado
) else (
    echo ERRO: Node.js 18.19.0 nao encontrado!
    echo Execute setup-completo-projeto.bat primeiro
    pause
    exit /b 1
)

echo.
echo ========================================
echo    FERRAMENTAS CONFIGURADAS
echo ========================================
echo Java: !JAVA_HOME!
echo Maven: !MAVEN_HOME!
echo Node.js: !NODE_HOME!
echo.

echo Escolha o servico para iniciar:
echo 1. Backend Spring Boot (Oracle)
echo 2. Frontend Angular
echo 3. NGROK para APIs bancarias
echo 4. Todos os servicos
echo.

set /p escolha="Digite sua escolha (1-4): "

if "%escolha%"=="1" goto :backend
if "%escolha%"=="2" goto :frontend
if "%escolha%"=="3" goto :ngrok
if "%escolha%"=="4" goto :todos
goto :invalido

:backend
echo.
echo Iniciando Backend Spring Boot...
cd backend
echo Executando: mvn spring-boot:run -Dspring.profiles.active=dev
mvn spring-boot:run -Dspring.profiles.active=dev
goto :fim

:frontend
echo.
echo Iniciando Frontend Angular...
cd frontend
echo Instalando dependencias...
npm install
echo Executando: ng serve
ng serve
goto :fim

:ngrok
echo.
echo Iniciando NGROK para APIs bancarias...
cd scripts
if exist "start-ngrok.bat" (
    start-ngrok.bat
) else (
    echo ERRO: Script NGROK nao encontrado!
    echo Execute setup-completo-projeto.bat primeiro
)
goto :fim

:todos
echo.
echo Iniciando todos os servicos...
echo.
echo 1. Backend em nova janela...
start "Backend Spring Boot" cmd /k "cd /d %CD%\backend && mvn spring-boot:run -Dspring.profiles.active=dev"
echo.
echo 2. Frontend em nova janela...
start "Frontend Angular" cmd /k "cd /d %CD%\frontend && npm install && ng serve"
echo.
echo 3. NGROK em nova janela...
if exist "scripts\start-ngrok.bat" (
    start "NGROK" cmd /k "cd /d %CD%\scripts && start-ngrok.bat"
) else (
    echo ERRO: Script NGROK nao encontrado!
)
echo.
echo Todos os servicos iniciados em janelas separadas!
echo.
echo URLs disponiveis:
echo - Backend: http://localhost:8080
echo - Frontend: http://localhost:4200
echo - Swagger: http://localhost:8080/swagger-ui/
echo.
goto :fim

:invalido
echo Escolha invalida! Digite 1, 2, 3 ou 4.
pause
goto :fim

:fim
echo.
echo Pressione qualquer tecla para sair...
pause >nul
