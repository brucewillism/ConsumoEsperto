@echo off
REM =============================================================================
REM MENU INTERATIVO - SETUP COMPLETO DO PROJETO CONSUMO ESPERTO
REM =============================================================================
REM Este script oferece um menu interativo para configurar e instalar
REM todas as ferramentas necessárias para o projeto ConsumoEsperto
REM =============================================================================

:menu
cls
echo.
echo =============================================================================
echo    MENU INTERATIVO - SETUP COMPLETO DO PROJETO CONSUMO ESPERTO
echo =============================================================================
echo.
echo [1]  Configurar JAVA_HOME (Java 17)
echo [2]  Verificar instalação do Java
echo [3]  Instalar Java 17+ (Eclipse Temurin)
echo [4]  Verificar instalação do Maven
echo [5]  Instalar Maven 3.9.6+
echo [6]  Verificar instalação do Node.js
echo [7]  Instalar Node.js 20+
echo [8]  Configurar variáveis de ambiente (.env)
echo [9]  Verificar todas as ferramentas
echo [10] Instalar todas as ferramentas (Java + Maven + Node.js)
echo [11] Configuração rápida completa
echo [0]  Sair
echo.
echo =============================================================================
set /p opcao="Escolha uma opção: "

if "%opcao%"=="1" goto configurar_java_home
if "%opcao%"=="2" goto verificar_java
if "%opcao%"=="3" goto instalar_java
if "%opcao%"=="4" goto verificar_maven
if "%opcao%"=="5" goto instalar_maven
if "%opcao%"=="6" goto verificar_node
if "%opcao%"=="7" goto instalar_node
if "%opcao%"=="8" goto configurar_env
if "%opcao%"=="9" goto verificar_tudo
if "%opcao%"=="10" goto instalar_tudo
if "%opcao%"=="11" goto configuracao_rapida
if "%opcao%"=="0" goto sair
goto menu

:configurar_java_home
cls
echo.
echo =============================================================================
echo    CONFIGURANDO JAVA_HOME
echo =============================================================================
echo.
call configurar-java-home.bat
echo.
pause
goto menu

:verificar_java
cls
echo.
echo =============================================================================
echo    VERIFICANDO INSTALAÇÃO DO JAVA
echo =============================================================================
echo.

set SCRIPT_DIR=%~dp0
set JAVA_HOME_LOCAL=%SCRIPT_DIR%tools\java\ms-17.0.15

if exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
    echo [OK] Java encontrado em: %JAVA_HOME_LOCAL%
    echo.
    echo Verificando versão...
    "%JAVA_HOME_LOCAL%\bin\java.exe" -version
    echo.
    if defined JAVA_HOME (
        echo [OK] JAVA_HOME configurado: %JAVA_HOME%
    ) else (
        echo [AVISO] JAVA_HOME não está configurado nesta sessão
        echo Execute a opção [1] para configurar
    )
) else (
    echo [ERRO] Java não encontrado em: %JAVA_HOME_LOCAL%
    echo.
    echo Execute a opção [3] para instalar o Java
)

echo.
pause
goto menu

:instalar_java
cls
echo.
echo =============================================================================
echo    INSTALANDO JAVA 17+ (ECLIPSE TEMURIN)
echo =============================================================================
echo.
echo Executando instalador PowerShell...
powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool java
echo.
pause
goto menu

:verificar_maven
cls
echo.
echo =============================================================================
echo    VERIFICANDO INSTALAÇÃO DO MAVEN
echo =============================================================================
echo.

set SCRIPT_DIR=%~dp0
set MAVEN_HOME_LOCAL=%SCRIPT_DIR%tools\maven

if exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    echo [OK] Maven encontrado em: %MAVEN_HOME_LOCAL%
    echo.
    echo Verificando versão...
    "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" -version
) else (
    echo [ERRO] Maven não encontrado em: %MAVEN_HOME_LOCAL%
    echo.
    echo Execute a opção [5] para instalar o Maven
)

echo.
pause
goto menu

:instalar_maven
cls
echo.
echo =============================================================================
echo    INSTALANDO MAVEN 3.9.6+
echo =============================================================================
echo.
echo Executando instalador PowerShell...
powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool maven
echo.
pause
goto menu

:verificar_node
cls
echo.
echo =============================================================================
echo    VERIFICANDO INSTALAÇÃO DO NODE.JS
echo =============================================================================
echo.

set SCRIPT_DIR=%~dp0
set NODE_HOME_LOCAL=%SCRIPT_DIR%tools\node

if exist "%NODE_HOME_LOCAL%\node.exe" (
    echo [OK] Node.js encontrado em: %NODE_HOME_LOCAL%
    echo.
    echo Verificando versão...
    "%NODE_HOME_LOCAL%\node.exe" -v
    echo.
    echo Verificando npm...
    "%NODE_HOME_LOCAL%\npm.cmd" -v
) else (
    echo [ERRO] Node.js não encontrado em: %NODE_HOME_LOCAL%
    echo.
    echo Execute a opção [7] para instalar o Node.js
)

echo.
pause
goto menu

:instalar_node
cls
echo.
echo =============================================================================
echo    INSTALANDO NODE.JS 20+
echo =============================================================================
echo.
echo Executando instalador PowerShell...
powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool node
echo.
pause
goto menu

:configurar_env
cls
echo.
echo =============================================================================
echo    CONFIGURANDO VARIÁVEIS DE AMBIENTE
echo =============================================================================
echo.
if exist "env.example" (
    echo Arquivo env.example encontrado!
    echo.
    echo Copiando para .env...
    copy env.example .env >nul
    echo [OK] Arquivo .env criado
    echo.
    echo Por favor, edite o arquivo .env com suas credenciais reais.
    echo.
    echo Deseja abrir o arquivo .env agora? (S/N)
    set /p abrir="> "
    if /i "%abrir%"=="S" (
        notepad .env
    )
) else (
    echo [AVISO] Arquivo env.example não encontrado
    echo Criando arquivo .env básico...
    (
        echo # Variáveis de Ambiente - ConsumoEsperto
        echo DATABASE_URL=jdbc:postgresql://localhost:5432/consumoesperto
        echo DATABASE_USERNAME=seu_usuario
        echo DATABASE_PASSWORD=sua_senha
        echo JWT_SECRET=sua_chave_secreta_jwt
        echo.
    ) > .env
    echo [OK] Arquivo .env criado
)

echo.
pause
goto menu

:verificar_tudo
cls
echo.
echo =============================================================================
echo    VERIFICANDO TODAS AS FERRAMENTAS
echo =============================================================================
echo.

set SCRIPT_DIR=%~dp0
set JAVA_HOME_LOCAL=%SCRIPT_DIR%tools\java\ms-17.0.15
set MAVEN_HOME_LOCAL=%SCRIPT_DIR%tools\maven
set NODE_HOME_LOCAL=%SCRIPT_DIR%tools\node

echo [1/3] Verificando Java...
if exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
    echo     [OK] Java instalado
) else (
    echo     [FALTA] Java não instalado
)

echo [2/3] Verificando Maven...
if exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    echo     [OK] Maven instalado
) else (
    echo     [FALTA] Maven não instalado
)

echo [3/3] Verificando Node.js...
if exist "%NODE_HOME_LOCAL%\node.exe" (
    echo     [OK] Node.js instalado
) else (
    echo     [FALTA] Node.js não instalado
)

echo.
echo Verificando configurações...
if defined JAVA_HOME (
    echo     [OK] JAVA_HOME configurado
) else (
    echo     [AVISO] JAVA_HOME não configurado
)

if exist ".env" (
    echo     [OK] Arquivo .env existe
) else (
    echo     [AVISO] Arquivo .env não existe
)

echo.
pause
goto menu

:instalar_tudo
cls
echo.
echo =============================================================================
echo    INSTALANDO TODAS AS FERRAMENTAS
echo =============================================================================
echo.
echo Isso irá instalar: Java, Maven e Node.js
echo.
set /p confirmar="Deseja continuar? (S/N): "
if /i not "%confirmar%"=="S" goto menu

echo.
echo [1/3] Instalando Java...
powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool java
echo.
echo [2/3] Instalando Maven...
powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool maven
echo.
echo [3/3] Instalando Node.js...
powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool node
echo.
echo [OK] Todas as ferramentas instaladas!
echo.
pause
goto menu

:configuracao_rapida
cls
echo.
echo =============================================================================
echo    CONFIGURAÇÃO RÁPIDA COMPLETA
echo =============================================================================
echo.
echo Isso irá:
echo   1. Configurar JAVA_HOME
echo   2. Verificar/Instalar ferramentas faltantes
echo   3. Criar arquivo .env
echo.
set /p confirmar="Deseja continuar? (S/N): "
if /i not "%confirmar%"=="S" goto menu

echo.
echo [1/3] Configurando JAVA_HOME...
call configurar-java-home.bat
echo.
echo [2/3] Verificando ferramentas...
call :verificar_tudo_silencioso
echo.
echo [3/3] Configurando .env...
if not exist ".env" (
    if exist "env.example" (
        copy env.example .env >nul
        echo [OK] Arquivo .env criado a partir de env.example
    ) else (
        echo [AVISO] env.example não encontrado, criando .env básico...
        (
            echo # Variáveis de Ambiente - ConsumoEsperto
            echo DATABASE_URL=jdbc:postgresql://localhost:5432/consumoesperto
            echo DATABASE_USERNAME=seu_usuario
            echo DATABASE_PASSWORD=sua_senha
            echo JWT_SECRET=sua_chave_secreta_jwt
        ) > .env
    )
)
echo.
echo [OK] Configuração rápida concluída!
echo.
pause
goto menu

:verificar_tudo_silencioso
set SCRIPT_DIR=%~dp0
set JAVA_HOME_LOCAL=%SCRIPT_DIR%tools\java\ms-17.0.15
set MAVEN_HOME_LOCAL=%SCRIPT_DIR%tools\maven
set NODE_HOME_LOCAL=%SCRIPT_DIR%tools\node

if not exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
    echo [AVISO] Java não encontrado, instalando...
    powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool java
)
if not exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    echo [AVISO] Maven não encontrado, instalando...
    powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool maven
)
if not exist "%NODE_HOME_LOCAL%\node.exe" (
    echo [AVISO] Node.js não encontrado, instalando...
    powershell -ExecutionPolicy Bypass -File "tools\install-tools.ps1" -Tool node
)
exit /b

:sair
echo.
echo Obrigado por usar o Setup do ConsumoEsperto!
echo.
exit /b 0

