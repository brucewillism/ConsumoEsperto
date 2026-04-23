@echo off
echo ========================================
echo   RODANDO BACKEND - CONSUMO ESPERTO
echo ========================================
echo.

REM Verificar se Java está configurado
if "%JAVA_HOME%"=="" (
    echo [AVISO] JAVA_HOME não está configurado!
    echo Tentando configurar automaticamente...
    call configurar-java-home.bat
    if errorlevel 1 (
        echo [ERRO] Não foi possível configurar JAVA_HOME automaticamente.
        echo Por favor, execute configurar-java-home.bat manualmente.
        pause
        exit /b 1
    )
)

REM Navegar para o diretório backend
cd backend
if errorlevel 1 (
    echo [ERRO] Diretório backend não encontrado!
    pause
    exit /b 1
)

echo.
echo [INFO] Diretório atual: %CD%
echo [INFO] Java: %JAVA_HOME%
echo [INFO] Porta: 8080
echo [INFO] Ngrok URL: https://79c3d0af5801.ngrok-free.app
echo.
echo [INFO] Iniciando aplicação Spring Boot...
echo.

REM Rodar o Spring Boot
call mvn spring-boot:run

if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao iniciar a aplicação!
    echo.
    echo Verifique:
    echo - PostgreSQL está rodando?
    echo - Banco de dados existe?
    echo - Variáveis de ambiente configuradas?
    echo.
    pause
    exit /b 1
)

pause

