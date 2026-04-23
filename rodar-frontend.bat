@echo off
echo ========================================
echo   RODANDO FRONTEND - CONSUMO ESPERTO
echo ========================================
echo.

REM Navegar para o diretório frontend
cd frontend
if errorlevel 1 (
    echo [ERRO] Diretório frontend não encontrado!
    pause
    exit /b 1
)

echo.
echo [INFO] Diretório atual: %CD%
echo [INFO] Porta padrão: 4200
echo [INFO] Backend esperado em: http://localhost:8080
echo.
echo [INFO] Verificando Node.js...
where node >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Node.js não encontrado no PATH!
    echo Por favor, instale o Node.js ou adicione ao PATH.
    pause
    exit /b 1
)

echo [INFO] Node.js encontrado!
echo.
echo [INFO] Verificando dependências...
if not exist "node_modules" (
    echo [AVISO] node_modules não encontrado!
    echo [INFO] Instalando dependências...
    call npm install
    if errorlevel 1 (
        echo [ERRO] Falha ao instalar dependências!
        pause
        exit /b 1
    )
)

echo.
echo [INFO] Iniciando servidor de desenvolvimento Angular...
echo.
echo Acesse: http://localhost:4200
echo.
echo Pressione Ctrl+C para parar o servidor.
echo.

REM Rodar o Angular
call npm start

if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao iniciar o servidor!
    echo.
    echo Verifique:
    echo - Node.js está instalado?
    echo - Dependências instaladas? (npm install)
    echo - Porta 4200 está livre?
    echo.
    pause
    exit /b 1
)

pause

