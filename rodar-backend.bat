@echo off
setlocal EnableExtensions
echo ========================================
echo   RODANDO BACKEND - CONSUMO ESPERTO
echo ========================================
echo.

REM Raiz do repositorio (pasta onde esta este .bat)
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

REM Versao oficial: JDK e Maven da pasta tools (so nesta sessao do CMD)
set "JAVA_HOME=%ROOT%\tools\java\ms-17.0.15"
if not exist "%JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME="
  for /d %%J in ("%ROOT%\tools\java\*") do (
    set "JAVA_HOME=%%~fJ"
    goto :have_java
  )
)
:have_java
if not defined JAVA_HOME (
    echo [ERRO] Nenhum JDK em %ROOT%\tools\java
    echo Instale conforme tools\README.md
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%ROOT%\tools\maven\bin;%PATH%"

where java >nul 2>&1
if errorlevel 1 (
    echo [ERRO] java.exe nao encontrado apos configurar tools.
    pause
    exit /b 1
)

cd /d "%ROOT%\backend"
if errorlevel 1 (
    echo [ERRO] Diretorio backend nao encontrado!
    pause
    exit /b 1
)

echo.
echo [INFO] Diretorio atual: %CD%
echo [INFO] JAVA_HOME (tools): %JAVA_HOME%
echo [INFO] Porta padrao: 8080 (veja application.properties / profiles)
echo.
java -version 2>&1
echo.
echo [INFO] Iniciando Spring Boot (mvn spring-boot:run)...
echo.

call mvn spring-boot:run

if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao iniciar a aplicacao!
    echo.
    echo Verifique: PostgreSQL, banco, .env / application-*.properties
    echo.
    pause
    exit /b 1
)

pause
