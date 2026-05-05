@echo off
setlocal EnableExtensions
REM Evolution API em Node. Clone o repositorio oficial em tools\evolution-api ou defina EVOLUTION_DIR.
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

if defined EVOLUTION_DIR (
    set "EVO=%EVOLUTION_DIR%"
) else (
    set "EVO=%ROOT%\tools\evolution-api"
)

REM Alinha DATABASE_CONNECTION_URI e AUTHENTICATION_API_KEY com o ConsumoEsperto
if exist "%ROOT%\tools\evolution-api\.env" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\sincronizar-evolution-env.ps1"
)

if not exist "%EVO%\package.json" (
    echo [ERRO] Evolution API nao encontrada em: %EVO%
    echo Clone o projeto EvolutionAPI/evolution-api para essa pasta ou defina EVOLUTION_DIR.
    echo Exemplo: git clone https://github.com/EvolutionAPI/evolution-api.git "%EVO%"
    pause
    exit /b 1
)

if exist "%ROOT%\tools\node\node.exe" (
    set "PATH=%ROOT%\tools\node;%PATH%"
    echo [INFO] Node: tools\node
) else (
    echo [INFO] Node: PATH do sistema
)

where node >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Node.js nao encontrado.
    pause
    exit /b 1
)

cd /d "%EVO%"
echo [INFO] Pasta Evolution: %CD%
echo [INFO] Porta padrao da API: 8080 (ajuste .env se precisar)
echo [INFO] Primeira vez: npm install, configure .env, npm run build, db deploy conforme doc oficial.
echo.

if not exist "dist\main.js" (
    echo [ERRO] Build nao encontrado (dist\main.js). Na pasta da Evolution execute:
    echo   npm install
    echo   npm run build
    echo   (e migracoes DB conforme doc oficial)
    pause
    exit /b 1
)
call npm run start:prod

pause
