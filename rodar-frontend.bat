@echo off
setlocal EnableExtensions
echo ========================================
echo   RODANDO FRONTEND - CONSUMO ESPERTO
echo ========================================
echo.

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

cd /d "%ROOT%\frontend"
if errorlevel 1 (
    echo [ERRO] Diretorio frontend nao encontrado!
    pause
    exit /b 1
)

REM Node oficial do repo, se existir (so nesta sessao)
if exist "%ROOT%\tools\node\node.exe" (
    set "PATH=%ROOT%\tools\node;%PATH%"
    echo [INFO] Usando Node de: %ROOT%\tools\node
) else (
    echo [INFO] tools\node vazio — usando Node do PATH do sistema.
)

where node >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Node.js nao encontrado!
    echo Instale Node ou preencha tools\node (veja tools\README.md)
    pause
    exit /b 1
)

echo.
echo [INFO] Diretorio atual: %CD%
echo [INFO] Porta: 4200  |  Backend esperado: http://localhost:8081
echo.

if not exist "node_modules" (
    echo [INFO] Instalando dependencias...
    call npm install
    if errorlevel 1 (
        echo [ERRO] Falha no npm install
        pause
        exit /b 1
    )
)

echo [INFO] Iniciando ng serve...
echo Acesse: http://localhost:4200
echo.
call npm start

if errorlevel 1 (
    echo [ERRO] Falha ao iniciar o servidor!
    pause
    exit /b 1
)

pause
