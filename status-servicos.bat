@echo off
chcp 65001 >nul
echo ========================================
echo    STATUS DOS SERVIÇOS - CONSUMO ESPERTO
echo ========================================
echo.

:: Verificar ferramentas instaladas
echo [1/4] VERIFICANDO FERRAMENTAS INSTALADAS:
echo ----------------------------------------
set "TOOLS_DIR=%~dp0tools"

if exist "%TOOLS_DIR%\java-17\bin\java.exe" (
    echo ✅ Java 17: Instalado
    "%TOOLS_DIR%\java-17\bin\java.exe" -version 2>&1 | findstr "version"
) else (
    echo ❌ Java 17: Não instalado
)

if exist "%TOOLS_DIR%\maven-3.9.6\bin\mvn.cmd" (
    echo ✅ Maven 3.9.6: Instalado
    "%TOOLS_DIR%\maven-3.9.6\bin\mvn.cmd" -version 2>&1 | findstr "Apache Maven"
) else (
    echo ❌ Maven 3.9.6: Não instalado
)

if exist "%TOOLS_DIR%\node-20.11.0\node.exe" (
    echo ✅ Node.js 20.11.0: Instalado
    "%TOOLS_DIR%\node-20.11.0\node.exe" --version
) else (
    echo ❌ Node.js 20.11.0: Não instalado
)

if exist "%TOOLS_DIR%\ngrok.exe" (
    echo ✅ ngrok: Instalado
) else (
    echo ❌ ngrok: Não instalado
)

echo.

:: Verificar serviços rodando
echo [2/4] VERIFICANDO SERVIÇOS EM EXECUÇÃO:
echo ----------------------------------------

:: Backend (porta 8080)
echo Backend (porta 8080):
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :8080') do (
    echo ✅ Rodando - Porta 8080
    goto :backend_status
)
echo ❌ Não está rodando

:backend_status

:: Frontend (porta 4200)
echo Frontend (porta 4200):
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :4200') do (
    echo ✅ Rodando - Porta 4200
    goto :frontend_status
)
echo ❌ Não está rodando

:frontend_status

:: ngrok (porta 4040)
echo ngrok (porta 4040):
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :4040') do (
    echo ✅ Rodando - Porta 4040
    goto :ngrok_status
)
echo ❌ Não está rodando

:ngrok_status

echo.

:: Verificar URLs disponíveis
echo [3/4] VERIFICANDO URLs DISPONÍVEIS:
echo -----------------------------------

:: Tentar obter URL do ngrok
echo Obtendo URL do ngrok...
for /f "tokens=*" %%i in ('powershell -Command "try { $ngrokInfo = Invoke-RestMethod -Uri 'http://localhost:4040/api/tunnels' -ErrorAction SilentlyContinue; if ($ngrokInfo.tunnels -and $ngrokInfo.tunnels.Count -gt 0) { $ngrokInfo.tunnels[0].public_url } } catch { 'N/A' }"') do set "NGROK_URL=%%i"

if "!NGROK_URL!"=="N/A" set "NGROK_URL=Não disponível"

echo URLs disponíveis:
echo Backend: http://localhost:8080
echo Frontend: http://localhost:4200
echo ngrok: !NGROK_URL!
echo Swagger: http://localhost:8080/swagger-ui.html
echo Health Check: http://localhost:8080/actuator/health

echo.

:: Verificar arquivos de configuração
echo [4/4] VERIFICANDO CONFIGURAÇÕES:
echo ---------------------------------

if exist "backend\src\main\resources\bank-apis-config.properties" (
    echo ✅ Configuração das APIs bancárias: OK
) else (
    echo ❌ Configuração das APIs bancárias: Faltando
)

if exist "backend\env-vars.txt" (
    echo ✅ Variáveis de ambiente: OK
) else (
    echo ❌ Variáveis de ambiente: Faltando
)

if exist "scripts\setup-ngrok.ps1" (
    echo ✅ Scripts de configuração: OK
) else (
    echo ❌ Scripts de configuração: Faltando
)

echo.
echo ========================================
echo    RESUMO DO STATUS
echo ========================================
echo.

:: Contar ferramentas instaladas
set "tools_count=0"
if exist "%TOOLS_DIR%\java-17\bin\java.exe" set /a tools_count+=1
if exist "%TOOLS_DIR%\maven-3.9.6\bin\mvn.cmd" set /a tools_count+=1
if exist "%TOOLS_DIR%\node-20.11.0\node.exe" set /a tools_count+=1
if exist "%TOOLS_DIR%\ngrok.exe" set /a tools_count+=1

:: Contar serviços rodando
set "services_count=0"
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :8080') do set /a services_count+=1
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :4200') do set /a services_count+=1
for /f "tokens=*" %%a in ('netstat -an 2^>nul ^| findstr :4040') do set /a services_count+=1

echo Ferramentas instaladas: %tools_count%/4
echo Serviços rodando: %services_count%/3
echo.

if %tools_count%==4 (
    echo ✅ Todas as ferramentas estão instaladas
) else (
    echo ⚠️  Execute setup-completo-projeto.bat para instalar ferramentas
)

if %services_count%==3 (
    echo ✅ Todos os serviços estão rodando
) else (
    echo ⚠️  Execute start-servicos.bat para iniciar serviços
)

echo.
echo ========================================
echo    COMANDOS DISPONÍVEIS
echo ========================================
echo.
echo Para instalar tudo: setup-completo-projeto.bat
echo Para iniciar serviços: start-servicos.bat
echo Para parar serviços: parar-servicos.bat
echo Para verificar status: status-servicos.bat
echo.
pause
