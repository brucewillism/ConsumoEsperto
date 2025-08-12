@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    INICIANDO SERVIÇOS - CONSUMO ESPERTO
echo ========================================
echo.

:: Verificar se as ferramentas estão instaladas
set "TOOLS_DIR=%~dp0tools"
set "JAVA_HOME_LOCAL=%TOOLS_DIR%\java-17"
set "MAVEN_HOME_LOCAL=%TOOLS_DIR%\maven-3.9.6"
set "NODE_HOME_LOCAL=%TOOLS_DIR%\node-20.11.0"

if not exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
    echo Java 17 não encontrado! Execute setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

if not exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    echo Maven não encontrado! Execute setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

if not exist "%NODE_HOME_LOCAL%\node.exe" (
    echo Node.js não encontrado! Execute setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

echo Ferramentas encontradas! Iniciando serviços...
echo.

:: Definir diretórios do projeto
set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "FRONTEND_DIR=%PROJECT_DIR%frontend"

:: Verificar se os serviços já estão rodando
echo Verificando serviços em execução...

:: Verificar backend (porta 8080)
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :8080') do (
    echo Backend já está rodando na porta 8080
    goto :backend_running
)
echo Backend não está rodando

:: Verificar frontend (porta 4200)
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :4200') do (
    echo Frontend já está rodando na porta 4200
    goto :frontend_running
)
echo Frontend não está rodando

:: Verificar ngrok (porta 4040)
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :4040') do (
    echo ngrok já está rodando na porta 4040
    goto :ngrok_running
)
echo ngrok não está rodando

echo.
echo ========================================
echo    INICIANDO SERVIÇOS...
echo ========================================
echo.

:: Iniciar backend se não estiver rodando
:backend_running
if not exist "%TEMP_DIR%\backend_running.flag" (
    echo Iniciando backend...
    start "Backend - Consumo Esperto" /min cmd /c "cd /d \"%BACKEND_DIR%\" && \"%MAVEN_HOME_LOCAL%\bin\mvn.cmd\" spring-boot:run"
    
    :: Aguardar backend iniciar
    echo Aguardando backend iniciar...
    timeout /t 15 /nobreak >nul
    
    :: Marcar como rodando
    echo. > "%TEMP_DIR%\backend_running.flag"
) else (
    echo Backend já foi iniciado.
)

:: Iniciar ngrok se não estiver rodando
:ngrok_running
if not exist "%TEMP_DIR%\ngrok_running.flag" (
    echo Iniciando ngrok...
    start "ngrok" /min cmd /c "cd /d \"%TOOLS_DIR%\" && ngrok http 8080"
    
    :: Aguardar ngrok iniciar
    echo Aguardando ngrok iniciar...
    timeout /t 10 /nobreak >nul
    
    :: Marcar como rodando
    echo. > "%TEMP_DIR%\ngrok_running.flag"
) else (
    echo ngrok já foi iniciado.
)

:: Iniciar frontend se não estiver rodando
:frontend_running
if not exist "%TEMP_DIR%\frontend_running.flag" (
    echo Iniciando frontend...
    start "Frontend - Consumo Esperto" /min cmd /c "cd /d \"%FRONTEND_DIR%\" && \"%NODE_HOME_LOCAL%\npm.cmd\" start"
    
    :: Aguardar frontend iniciar
    echo Aguardando frontend iniciar...
    timeout /t 20 /nobreak >nul
    
    :: Marcar como rodando
    echo. > "%TEMP_DIR%\frontend_running.flag"
) else (
    echo Frontend já foi iniciado.
)

:: Aguardar um pouco mais para garantir que tudo está rodando
echo Aguardando serviços estabilizarem...
timeout /t 10 /nobreak >nul

:: Tentar obter URL do ngrok
echo Obtendo URL do ngrok...
for /f "tokens=*" %%i in ('powershell -Command "try { $ngrokInfo = Invoke-RestMethod -Uri 'http://localhost:4040/api/tunnels' -ErrorAction SilentlyContinue; if ($ngrokInfo.tunnels -and $ngrokInfo.tunnels.Count -gt 0) { $ngrokInfo.tunnels[0].public_url } } catch { 'http://localhost:4200' }"') do set "NGROK_URL=%%i"

if "!NGROK_URL!"=="" set "NGROK_URL=http://localhost:4200"

:: Abrir navegador
echo Abrindo navegador...
start "" "!NGROK_URL!"

echo.
echo ========================================
echo    SERVIÇOS INICIADOS!
echo ========================================
echo.
echo Backend: http://localhost:8080
echo Frontend: http://localhost:4200
echo ngrok: !NGROK_URL!
echo.
echo Swagger: http://localhost:8080/swagger-ui.html
echo Health Check: http://localhost:8080/actuator/health
echo.
echo ========================================
echo    COMANDOS ÚTEIS:
echo ========================================
echo.
echo Para parar todos os serviços: parar-servicos.bat
echo Para reiniciar: execute este script novamente
echo.
echo ========================================
echo    APLICAÇÃO RODANDO!
echo ========================================
echo.

:: Manter janela aberta
pause
