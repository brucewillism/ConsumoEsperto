@echo off
setlocal EnableExtensions
REM Backend com perfil dev-evolution na porta 8081 (Evolution API usa 8080).
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

REM Preferir JDK documentado em tools\README.md (evita ordem aleatoria se houver mais pastas em tools\java).
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
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%ROOT%\tools\maven\bin;%PATH%"

cd /d "%ROOT%\backend"
echo [INFO] JAVA_HOME=%JAVA_HOME%
echo [INFO] Spring Boot: porta 8081, profile dev-evolution
echo.
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [ERRO] Porta 8081 ja esta em uso — outro backend ou processo esta a escutar.
  echo        Fecha o terminal antigo do Spring ou executa parar-servicos.bat na raiz do projeto.
  echo.
  netstat -ano | findstr ":8081" | findstr "LISTENING"
  echo.
  pause
  exit /b 1
)
call mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081 --spring.profiles.active=dev-evolution"
if errorlevel 1 pause
exit /b %errorlevel%
