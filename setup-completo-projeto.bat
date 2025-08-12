@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    SETUP COMPLETO DO PROJETO CONSUMO ESPERTO
echo ========================================
echo.
echo Este script irá configurar todo o ambiente de desenvolvimento:
echo - JDK 17, Maven 3.9.6, Node.js 20.11.0
echo - Dependências do backend e frontend
echo - Banco de dados Oracle
echo - Configurações do IntelliJ IDEA
echo - Configurações do Maven
echo - Arquivos .gitignore
echo - Documentação completa
echo.

:: Definir diretórios do projeto
set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "FRONTEND_DIR=%PROJECT_DIR%frontend"
set "TEMP_DIR=%PROJECT_DIR%temp"
set "TOOLS_DIR=%PROJECT_DIR%tools"
set "DB_DIR=%BACKEND_DIR%\src\main\resources\db"

:: Criar diretórios temporários se não existirem
if not exist "%TEMP_DIR%" mkdir "%TEMP_DIR%"
if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"

:: Configurar variáveis locais (sem afetar o sistema)
set "JAVA_HOME_LOCAL=%TOOLS_DIR%\jdk-17.0.15"
set "MAVEN_HOME_LOCAL=%TOOLS_DIR%\maven-3.9.6"
set "NODE_HOME_LOCAL=%TOOLS_DIR%\node-20.11.0"
set "PATH_LOCAL=%JAVA_HOME_LOCAL%\bin;%MAVEN_HOME_LOCAL%\bin;%NODE_HOME_LOCAL%;%PATH%"

echo [1/11] Verificando JDKs já instalados no sistema...
echo.
echo Procurando JDKs existentes...
if exist "C:\Users\bruce.silva\.jdks\jbr-17.0.14\bin\java.exe" (
    set "JDK_PATH=C:\Users\bruce.silva\.jdks\jbr-17.0.14"
    echo ✅ JDK 17 encontrado: %JDK_PATH%
    set "USE_EXISTING_JDK=true"
) else if exist "C:\Users\bruce.silva\.jdks\ms-17.0.15\bin\java.exe" (
    set "JDK_PATH=C:\Users\bruce.silva\.jdks\ms-17.0.15"
    echo ✅ JDK 17 encontrado: %JDK_PATH%
    set "USE_EXISTING_JDK=true"
) else (
    echo Nenhum JDK 17 encontrado, baixando JDK 17 completo...
    set "USE_EXISTING_JDK=false"
    
if not exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
    echo Baixando JDK 17 completo...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip' -OutFile '%TEMP_DIR%\jdk-17.0.2.zip' -UseBasicParsing}"
    
    if exist "%TEMP_DIR%\jdk-17.0.2.zip" (
        echo Extraindo JDK 17...
        powershell -Command "Expand-Archive -Path '%TEMP_DIR%\jdk-17.0.2.zip' -DestinationPath '%TEMP_DIR%' -Force"
        xcopy "%TEMP_DIR%\jdk-17.0.2" "%JAVA_HOME_LOCAL%" /E /I /Y >nul
            set "JDK_PATH=%JAVA_HOME_LOCAL%"
            echo JDK 17 instalado com sucesso!
        ) else (
            echo ERRO: Falha ao baixar JDK 17
            pause
            exit /b 1
    )
) else (
        set "JDK_PATH=%JAVA_HOME_LOCAL%"
    echo JDK 17 já está instalado localmente.
    )
)

echo [2/11] Verificando e baixando Maven 3.9.6...
if not exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    echo Baixando Maven 3.9.6...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP_DIR%\maven-3.9.6.zip' -UseBasicParsing}"
    
    if exist "%TEMP_DIR%\maven-3.9.6.zip" (
        echo Extraindo Maven...
        powershell -Command "Expand-Archive -Path '%TEMP_DIR%\maven-3.9.6.zip' -DestinationPath '%TEMP_DIR%' -Force"
        xcopy "%TEMP_DIR%\apache-maven-3.9.6" "%MAVEN_HOME_LOCAL%" /E /I /Y >nul
        echo Maven instalado com sucesso!
    ) else (
        echo ERRO: Falha ao baixar Maven
        pause
        exit /b 1
    )
) else (
    echo Maven já está instalado localmente.
)

echo [3/11] Verificando e baixando Node.js 20.11.0...
if not exist "%NODE_HOME_LOCAL%\node.exe" (
    echo Baixando Node.js 20.11.0...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://nodejs.org/dist/v20.11.0/node-v20.11.0-win-x64.zip' -OutFile '%TEMP_DIR%\node-20.11.0.zip' -UseBasicParsing}"
    
    if exist "%TEMP_DIR%\node-20.11.0.zip" (
        echo Extraindo Node.js...
        powershell -Command "Expand-Archive -Path '%TEMP_DIR%\node-20.11.0.zip' -DestinationPath '%TEMP_DIR%' -Force"
        xcopy "%TEMP_DIR%\node-v20.11.0-win-x64" "%NODE_HOME_LOCAL%" /E /I /Y >nul
        echo Node.js instalado com sucesso!
    ) else (
        echo ERRO: Falha ao baixar Node.js
        pause
        exit /b 1
    )
) else (
    echo Node.js já está instalado localmente.
)

echo [4/11] Verificando e baixando ngrok...
if not exist "%TOOLS_DIR%\ngrok.exe" (
    echo Baixando ngrok...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip' -OutFile '%TEMP_DIR%\ngrok.zip' -UseBasicParsing}"
    
    if exist "%TEMP_DIR%\ngrok.zip" (
        echo Extraindo ngrok...
        powershell -Command "Expand-Archive -Path '%TEMP_DIR%\ngrok.zip' -DestinationPath '%TOOLS_DIR%' -Force"
        echo ngrok instalado com sucesso!
    ) else (
        echo ERRO: Falha ao baixar ngrok
        pause
        exit /b 1
    )
) else (
    echo ngrok já está instalado localmente.
)

echo [5/11] Verificando versões instaladas...
echo.
echo Java:
"%JDK_PATH%\bin\java.exe" -version 2>&1 | findstr "version"
echo.
echo Maven:
"%MAVEN_HOME_LOCAL%\bin\mvn.cmd" -version 2>&1 | findstr "Apache Maven"
echo.
echo Node.js:
"%NODE_HOME_LOCAL%\node.exe" --version
echo.
echo npm:
"%NODE_HOME_LOCAL%\npm.cmd" --version
echo.

echo [6/11] CONFIGURANDO ARQUIVOS DO INTELLIJ IDEA...
echo.
echo Criando configurações do IntelliJ IDEA...

:: Criar diretório .idea se não existir
if not exist "%BACKEND_DIR%\.idea" mkdir "%BACKEND_DIR%\.idea"

:: Criar misc.xml
echo Criando misc.xml...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > "%BACKEND_DIR%\.idea\misc.xml"
echo ^<project version="4"^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo   ^<component name="ExternalStorageConfigurationManager" enabled="true" /^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo   ^<component name="MavenProjectsManager"^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo     ^<option name="originalFiles"^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo       ^<list^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo         ^<option value="$PROJECT_DIR$/backend/pom.xml" /^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo       ^</list^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo     ^</option^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo   ^</component^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo   ^<component name="ProjectRootManager" version="2" languageLevel="JDK_17" default="true" project-jdk-name="%JDK_PATH%" project-jdk-type="JavaSDK"^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo     ^<output url="file://$PROJECT_DIR$/backend/out" /^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo   ^</component^> >> "%BACKEND_DIR%\.idea\misc.xml"
echo ^</project^> >> "%BACKEND_DIR%\.idea\misc.xml"

:: Criar modules.xml
echo Criando modules.xml...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > "%BACKEND_DIR%\.idea\modules.xml"
echo ^<project version="4"^> >> "%BACKEND_DIR%\.idea\modules.xml"
echo   ^<component name="ProjectModuleManager"^> >> "%BACKEND_DIR%\.idea\modules.xml"
echo     ^<modules^> >> "%BACKEND_DIR%\.idea\modules.xml"
echo       ^<module fileurl="file://$PROJECT_DIR$/backend/consumo-esperto-backend.iml" filepath="$PROJECT_DIR$/backend/consumo-esperto-backend.iml" /^> >> "%BACKEND_DIR%\.idea\modules.xml"
echo     ^</modules^> >> "%BACKEND_DIR%\.idea\modules.xml"
echo   ^</component^> >> "%BACKEND_DIR%\.idea\modules.xml"
echo ^</project^> >> "%BACKEND_DIR%\.idea\modules.xml"

:: Criar consumo-esperto-backend.iml
echo Criando consumo-esperto-backend.iml...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo ^<module type="JAVA_MODULE" version="4"^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo   ^<component name="FacetManager"^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo     ^<facet type="Spring" name="Spring"^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo       ^<configuration^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo         ^<enableAutoConfiguration^>true^</enableAutoConfiguration^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo       ^</configuration^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo     ^</facet^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo   ^</component^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo   ^<component name="NewModuleRootManager" LANGUAGE_LEVEL="JDK_17"^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo     ^<content url="file://$MODULE_DIR$"^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo       ^<sourceFolder url="file://$MODULE_DIR$/src/main/java" isTestSource="false" /^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo       ^<sourceFolder url="file://$MODULE_DIR$/src/main/resources" type="java-resource" /^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo       ^<sourceFolder url="file://$MODULE_DIR$/src/test/java" isTestSource="true" /^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo       ^<excludeFolder url="file://$MODULE_DIR$/target" /^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo     ^</content^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo     ^<orderEntry type="inheritedJdk" /^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo     ^<orderEntry type="sourceFolder" forTests="false" /^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo   ^</component^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"
echo ^</module^> >> "%BACKEND_DIR%\consumo-esperto-backend.iml"

:: Criar diretório .mvn se não existir
if not exist "%BACKEND_DIR%\.mvn" mkdir "%BACKEND_DIR%\.mvn"

:: Criar jvm.config
echo Criando jvm.config...
echo -Dmaven.compiler.source=17 > "%BACKEND_DIR%\.mvn\jvm.config"
echo -Dmaven.compiler.target=17 >> "%BACKEND_DIR%\.mvn\jvm.config"
echo -Dmaven.compiler.release=17 >> "%BACKEND_DIR%\.mvn\jvm.config"

echo ✅ Configurações do IntelliJ IDEA criadas com sucesso!

echo [7/11] CRIANDO ARQUIVOS .GITIGNORE...
echo.

:: Criar .gitignore raiz
echo Criando .gitignore raiz...
echo # Arquivos de build e dependências > .gitignore
echo target/ >> .gitignore
echo build/ >> .gitignore
echo out/ >> .gitignore
echo dist/ >> .gitignore
echo node_modules/ >> .gitignore
echo tools/ >> .gitignore
echo jdk*/ >> .gitignore
echo jre*/ >> .gitignore
echo *.jar >> .gitignore
echo *.war >> .gitignore
echo *.ear >> .gitignore
echo *.nar >> .gitignore
echo *.zip >> .gitignore
echo *.tar >> .gitignore
echo *.gz >> .gitignore
echo *.rar >> .gitignore
echo *.7z >> .gitignore
echo *.iso >> .gitignore
echo *.img >> .gitignore
echo *.bin >> .gitignore
echo *.exe >> .gitignore
echo *.dll >> .gitignore
echo *.so >> .gitignore
echo *.dylib >> .gitignore
echo logs/ >> .gitignore
echo *.tmp >> .gitignore
echo *.temp >> .gitignore
echo *.swp >> .gitignore
echo *.swo >> .gitignore
echo *~ >> .gitignore
echo *.db >> .gitignore
echo *.sqlite >> .gitignore
echo *.sqlite3 >> .gitignore
echo application-prod.properties >> .gitignore
echo application-prod.yml >> .gitignore
echo application-prod.yaml >> .gitignore
echo config/ >> .gitignore
echo secrets/ >> .gitignore
echo credentials/ >> .gitignore
echo *.key >> .gitignore
echo *.pem >> .gitignore
echo *.p12 >> .gitignore
echo *.pfx >> .gitignore
echo *.jks >> .gitignore
echo *.keystore >> .gitignore
echo *.truststore >> .gitignore
echo .cache/ >> .gitignore
echo cache/ >> .gitignore
echo tmp/ >> .gitignore
echo temp/ >> .gitignore
echo .DS_Store >> .gitignore
echo .DS_Store? >> .gitignore
echo ._* >> .gitignore
echo .Spotlight-V100 >> .gitignore
echo .Trashes >> .gitignore
echo ehthumbs.db >> .gitignore
echo Thumbs.db >> .gitignore
echo Desktop.ini >> .gitignore
echo .idea/ >> .gitignore
echo .vscode/ >> .gitignore
echo *.iml >> .gitignore
echo *.ipr >> .gitignore
echo *.iws >> .gitignore
echo .gradle/ >> .gitignore
echo .mvn/ >> .gitignore
echo !.mvn/wrapper/maven-wrapper.jar >> .gitignore
echo >> .gitignore
echo # Incluir arquivos de configuração importantes >> .gitignore
echo !application.properties >> .gitignore
echo !application.yml >> .gitignore
echo !application.yaml >> .gitignore
echo !application-h2.properties >> .gitignore
echo !bank-apis-config.properties >> .gitignore
echo !mercadopago-config.properties >> .gitignore

:: Criar .gitignore do backend
echo Criando .gitignore do backend...
echo # Compiled class files >> "%BACKEND_DIR%\.gitignore"
echo *.class >> "%BACKEND_DIR%\.gitignore"
echo >> "%BACKEND_DIR%\.gitignore"
echo # Log files >> "%BACKEND_DIR%\.gitignore"
echo *.log >> "%BACKEND_DIR%\.gitignore"
echo >> "%BACKEND_DIR%\.gitignore"
echo # Package Files >> "%BACKEND_DIR%\.gitignore"
echo *.jar >> "%BACKEND_DIR%\.gitignore"
echo *.war >> "%BACKEND_DIR%\.gitignore"
echo *.ear >> "%BACKEND_DIR%\.gitignore"
echo *.nar >> "%BACKEND_DIR%\.gitignore"
echo >> "%BACKEND_DIR%\.gitignore"
echo # Maven >> "%BACKEND_DIR%\.gitignore"
echo target/ >> "%BACKEND_DIR%\.gitignore"
echo >> "%BACKEND_DIR%\.gitignore"
echo # IDE >> "%BACKEND_DIR%\.gitignore"
echo .idea/ >> "%BACKEND_DIR%\.gitignore"
echo *.iml >> "%BACKEND_DIR%\.gitignore"
echo >> "%BACKEND_DIR%\.gitignore"
echo # Incluir configurações importantes >> "%BACKEND_DIR%\.gitignore"
echo !application.properties >> "%BACKEND_DIR%\.gitignore"
echo !application-h2.properties >> "%BACKEND_DIR%\.gitignore"
echo !bank-apis-config.properties >> "%BACKEND_DIR%\.gitignore"
echo !mercadopago-config.properties >> "%BACKEND_DIR%\.gitignore"

:: Criar .gitignore do frontend
echo Criando .gitignore do frontend...
echo # Dependencies >> "%FRONTEND_DIR%\.gitignore"
echo node_modules/ >> "%FRONTEND_DIR%\.gitignore"
echo >> "%FRONTEND_DIR%\.gitignore"
echo # Build outputs >> "%FRONTEND_DIR%\.gitignore"
echo dist/ >> "%FRONTEND_DIR%\.gitignore"
echo build/ >> "%FRONTEND_DIR%\.gitignore"
echo out/ >> "%FRONTEND_DIR%\.gitignore"
echo >> "%FRONTEND_DIR%\.gitignore"
echo # Logs >> "%FRONTEND_DIR%\.gitignore"
echo npm-debug.log* >> "%FRONTEND_DIR%\.gitignore"
echo yarn-debug.log* >> "%FRONTEND_DIR%\.gitignore"
echo >> "%FRONTEND_DIR%\.gitignore"
echo # IDE >> "%FRONTEND_DIR%\.gitignore"
echo .vscode/ >> "%FRONTEND_DIR%\.gitignore"
echo .idea/ >> "%FRONTEND_DIR%\.gitignore"

echo ✅ Arquivos .gitignore criados com sucesso!

echo [8/11] CRIANDO DOCUMENTAÇÃO AUTOMÁTICA...
echo.

:: Criar SOLUCAO_JDK_README.md
echo Criando SOLUCAO_JDK_README.md...
echo # ✅ PROBLEMA DO JDK RESOLVIDO! > "SOLUCAO_JDK_README.md"
echo. >> "SOLUCAO_JDK_README.md"
echo ## 🎯 O que foi o problema? >> "SOLUCAO_JDK_README.md"
echo O IntelliJ IDEA estava mostrando o erro: >> "SOLUCAO_JDK_README.md"
echo ``` >> "SOLUCAO_JDK_README.md"
echo java: JDK isn't specified for module 'consumo-esperto-backend' >> "SOLUCAO_JDK_README.md"
echo ``` >> "SOLUCAO_JDK_README.md"
echo. >> "SOLUCAO_JDK_README.md"
echo ## 🔍 O que foi encontrado? >> "SOLUCAO_JDK_README.md"
echo Seu sistema já tem **vários JDKs instalados**: >> "SOLUCAO_JDK_README.md"
echo - ✅ **%JDK_PATH%** (Java 17) ← **RECOMENDADO** >> "SOLUCAO_JDK_README.md"
echo. >> "SOLUCAO_JDK_README.md"
echo ## 🛠️ O que foi feito? >> "SOLUCAO_JDK_README.md"
echo 1. **Criados arquivos de configuração do IntelliJ** automaticamente >> "SOLUCAO_JDK_README.md"
echo 2. **Configurado Maven para usar JDK 17** automaticamente >> "SOLUCAO_JDK_README.md"
echo 3. **Tudo integrado no setup-completo-projeto.bat** >> "SOLUCAO_JDK_README.md"
echo. >> "SOLUCAO_JDK_README.md"
echo ## ✅ Resultado >> "SOLUCAO_JDK_README.md"
echo - **Projeto configurado automaticamente** usando JDK 17 >> "SOLUCAO_JDK_README.md"
echo - **IntelliJ IDEA configurado** sem intervenção manual >> "SOLUCAO_JDK_README.md"
echo - **Maven funcionando** com JDK 17 >> "SOLUCAO_JDK_README.md"
echo. >> "SOLUCAO_JDK_README.md"
echo ## 🚀 Como usar agora? >> "SOLUCAO_JDK_README.md"
echo 1. **Execute o setup completo**: `setup-completo-projeto.bat` >> "SOLUCAO_JDK_README.md"
echo 2. **Abra o projeto no IntelliJ IDEA** >> "SOLUCAO_JDK_README.md"
echo 3. **O JDK 17 já está configurado automaticamente!** >> "SOLUCAO_JDK_README.md"
echo. >> "SOLUCAO_JDK_README.md"
echo --- >> "SOLUCAO_JDK_README.md"
echo **Desenvolvido com ❤️ pela equipe Consumo Esperto** >> "SOLUCAO_JDK_README.md"

:: Criar CONFIGURACAO_BANCO_README.md
echo Criando CONFIGURACAO_BANCO_README.md...
echo # 🗄️ CONFIGURAÇÃO DO BANCO ORACLE >> "CONFIGURACAO_BANCO_README.md"
echo. >> "CONFIGURACAO_BANCO_README.md"
echo ## 📋 Pré-requisitos >> "CONFIGURACAO_BANCO_README.md"
echo - Oracle Database instalado e rodando >> "CONFIGURACAO_BANCO_README.md"
echo - SQL*Plus disponível >> "CONFIGURACAO_BANCO_README.md"
echo - Credenciais de administrador Oracle >> "CONFIGURACAO_BANCO_README.md"
echo. >> "CONFIGURACAO_BANCO_README.md"
echo ## 🚀 Configuração Automática >> "CONFIGURACAO_BANCO_README.md"
echo Execute o setup completo: >> "CONFIGURACAO_BANCO_README.md"
echo ```bash >> "CONFIGURACAO_BANCO_README.md"
echo setup-completo-projeto.bat >> "CONFIGURACAO_BANCO_README.md"
echo ``` >> "CONFIGURACAO_BANCO_README.md"
echo. >> "CONFIGURACAO_BANCO_README.md"
echo ## 📊 Tabelas Criadas >> "CONFIGURACAO_BANCO_README.md"
echo - usuarios >> "CONFIGURACAO_BANCO_README.md"
echo - categorias >> "CONFIGURACAO_BANCO_README.md"
echo - transacoes >> "CONFIGURACAO_BANCO_README.md"
echo - faturas >> "CONFIGURACAO_BANCO_README.md"
echo - compras_parceladas >> "CONFIGURACAO_BANCO_README.md"
echo - parcelas >> "CONFIGURACAO_BANCO_README.md"
echo - cartoes_credito >> "CONFIGURACAO_BANCO_README.md"
echo - autorizacoes_bancarias >> "CONFIGURACAO_BANCO_README.md"
echo - bank_api_configs >> "CONFIGURACAO_BANCO_README.md"
echo. >> "CONFIGURACAO_BANCO_README.md"
echo ## 🔧 Configuração Manual (se necessário) >> "CONFIGURACAO_BANCO_README.md"
echo ```bash >> "CONFIGURACAO_BANCO_README.md"
echo setup-database.bat >> "CONFIGURACAO_BANCO_README.md"
echo ``` >> "CONFIGURACAO_BANCO_README.md"
echo. >> "CONFIGURACAO_BANCO_README.md"
echo --- >> "CONFIGURACAO_BANCO_README.md"
echo **Desenvolvido com ❤️ pela equipe Consumo Esperto** >> "CONFIGURACAO_BANCO_README.md"

:: Criar CONFIGURACAO_APIS_BANCARIAS_README.md
echo Criando CONFIGURACAO_APIS_BANCARIAS_README.md...
echo # 🏦 CONFIGURAÇÃO DAS APIS BANCÁRIAS >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo. >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo ## 🔑 APIs Suportadas >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - **Mercado Pago** - Cartões de crédito e transações >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - **Nubank** - Cartões e transações >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - **Itaú** - Open Banking >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - **Inter** - Open Banking >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo. >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo ## 🚀 Configuração Automática >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo Execute o setup completo: >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo ```bash >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo setup-completo-projeto.bat >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo ``` >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo. >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo ## 💻 Interface Web >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo 1. Acesse: `http://localhost:8080/bank-config.html` >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo 2. Configure as credenciais de cada banco >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo 3. As configurações são salvas por usuário >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo. >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo ## 🔄 Sincronização Automática >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - Categorias sincronizadas automaticamente >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - Transações sincronizadas das APIs >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - Faturas sincronizadas das APIs >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo - Compras parceladas sincronizadas das APIs >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo. >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo --- >> "CONFIGURACAO_APIS_BANCARIAS_README.md"
echo **Desenvolvido com ❤️ pela equipe Consumo Esperto** >> "CONFIGURACAO_APIS_BANCARIAS_README.md"

:: Criar RESOLUCAO_PROBLEMAS_README.md
echo Criando RESOLUCAO_PROBLEMAS_README.md...
echo # 🚨 RESOLUÇÃO DE PROBLEMAS - CONSUMO ESPERTO > "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo Este documento contém soluções para problemas comuns que podem ocorrer durante o desenvolvimento e execução do projeto. >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ## 🔧 PROBLEMAS DO BACKEND >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ### Erro: `UnsatisfiedDependencyException: No property 'ativo' found for type 'AutorizacaoBancaria'` >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo **Causa**: A entidade `AutorizacaoBancaria` está faltando o campo `ativo`. >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo **Solução**: >> "RESOLUCAO_PROBLEMAS_README.md"
echo 1. Execute o script de resolução: `resolver-backend.bat` >> "RESOLUCAO_PROBLEMAS_README.md"
echo 2. Ou manualmente: >> "RESOLUCAO_PROBLEMAS_README.md"
echo    - Abra `backend/src/main/java/com/consumoesperto/model/AutorizacaoBancaria.java` >> "RESOLUCAO_PROBLEMAS_README.md"
echo    - Adicione o campo: `private Boolean ativo = true;` >> "RESOLUCAO_PROBLEMAS_README.md"
echo    - Recompile o projeto: `mvn clean compile` >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ## 🎨 PROBLEMAS DO FRONTEND >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ### Erro: `Cannot find module '@angular/material/...'` >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo **Causa**: Dependências do Angular Material não instaladas ou corrompidas. >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo **Solução**: >> "RESOLUCAO_PROBLEMAS_README.md"
echo 1. Execute o script de reinstalação: `reinstalar-frontend.bat` >> "RESOLUCAO_PROBLEMAS_README.md"
echo 2. Ou manualmente: >> "RESOLUCAO_PROBLEMAS_README.md"
echo    ```bash >> "RESOLUCAO_PROBLEMAS_README.md"
echo    cd frontend >> "RESOLUCAO_PROBLEMAS_README.md"
echo    rm -rf node_modules package-lock.json >> "RESOLUCAO_PROBLEMAS_README.md"
echo    npm install >> "RESOLUCAO_PROBLEMAS_README.md"
echo    ``` >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ## 🚀 SCRIPTS DE RESOLUÇÃO AUTOMÁTICA >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ### `resolver-backend.bat` >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Verifica Java e Maven >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Recompila o projeto >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Verifica entidades >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Verifica scripts de banco >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ### `reinstalar-frontend.bat` >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Remove dependências antigas >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Limpa cache >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Reinstala Angular Material >> "RESOLUCAO_PROBLEMAS_README.md"
echo - Verifica instalação >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ## 🎯 SOLUÇÕES RÁPIDAS >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo ^| Problema ^| Solução Rápida ^| >> "RESOLUCAO_PROBLEMAS_README.md"
echo ^|----------^|----------------^| >> "RESOLUCAO_PROBLEMAS_README.md"
echo ^| Backend não compila ^| `resolver-backend.bat` ^| >> "RESOLUCAO_PROBLEMAS_README.md"
echo ^| Frontend não roda ^| `reinstalar-frontend.bat` ^| >> "RESOLUCAO_PROBLEMAS_README.md"
echo ^| Banco não funciona ^| `setup-database.bat` ^| >> "RESOLUCAO_PROBLEMAS_README.md"
echo ^| Tudo quebrado ^| `setup-completo-projeto.bat` ^| >> "RESOLUCAO_PROBLEMAS_README.md"
echo. >> "RESOLUCAO_PROBLEMAS_README.md"
echo --- >> "RESOLUCAO_PROBLEMAS_README.md"
echo **Desenvolvido com ❤️ pela equipe Consumo Esperto** >> "RESOLUCAO_PROBLEMAS_README.md"

echo ✅ Documentação criada com sucesso!

echo [9/11] INSTALANDO DEPENDÊNCIAS DO BACKEND...
echo.
cd /d "%BACKEND_DIR%"
echo Instalando dependências Maven...
"%MAVEN_HOME_LOCAL%\bin\mvn.cmd" clean install -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Falha ao instalar dependências do backend
    pause
    exit /b 1
)
echo Dependências do backend instaladas com sucesso!

echo [10/11] INSTALANDO DEPENDÊNCIAS DO FRONTEND...
echo.
cd /d "%FRONTEND_DIR%"
echo Instalando dependências Node.js...
"%NODE_HOME_LOCAL%\npm.cmd" install --silent
if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Falha ao instalar dependências do frontend
    pause
    exit /b 1
)
echo Dependências do frontend instaladas com sucesso!

echo [11/11] CONFIGURAÇÃO DO BANCO DE DADOS ORACLE...
echo.
echo ========================================
echo    CONFIGURAÇÃO DO BANCO ORACLE
echo ========================================
echo.
echo Para configurar o banco Oracle, você precisa:
echo 1. Ter o Oracle Database instalado
echo 2. Ter o SQL*Plus disponível
echo 3. Conhecer as credenciais de administrador
echo.
echo Deseja configurar o banco Oracle agora? (S/N)
set /p CONFIG_ORACLE=

if /i "%CONFIG_ORACLE%"=="S" (
    echo.
    echo Configurando banco Oracle...
    echo.
    
    :: Solicitar credenciais Oracle
    echo Digite o usuário Oracle (ex: SYSTEM):
    set /p ORACLE_USER=
    echo Digite a senha Oracle:
    set /p ORACLE_PASS=
    echo Digite o host Oracle (ex: localhost):
    set /p ORACLE_HOST=
    echo Digite a porta Oracle (ex: 1521):
    set /p ORACLE_PORT=
    echo Digite o serviço Oracle (ex: XE):
    set /p ORACLE_SERVICE=
    
    echo.
    echo Criando banco de dados e tabelas...
    
    :: Criar script SQL temporário
    echo @%DB_DIR%\init-oracle.sql > "%TEMP_DIR%\create_db.sql"
    echo @%DB_DIR%\create-tables-only.sql >> "%TEMP_DIR%\create_db.sql"
    echo @%DB_DIR%\update_autorizacoes_table.sql >> "%TEMP_DIR%\create_db.sql"
    
    :: Executar script SQL
    sqlplus %ORACLE_USER%/%ORACLE_PASS%@%ORACLE_HOST%:%ORACLE_PORT%/%ORACLE_SERVICE% @"%TEMP_DIR%\create_db.sql"
    
    if %ERRORLEVEL% EQU 0 (
echo.
echo ========================================
        echo    BANCO ORACLE CONFIGURADO COM SUCESSO!
echo ========================================
echo.
        echo Detalhes da conexão:
        echo Host: %ORACLE_HOST%
        echo Porta: %ORACLE_PORT%
        echo Serviço: %ORACLE_SERVICE%
        echo Usuário: consumo_esperto
        echo Senha: consumo_esperto
echo.
        echo O banco 'consumo_esperto' foi criado com todas as tabelas necessárias.
        echo Os dados serão sincronizados automaticamente das APIs bancárias.
    ) else (
echo.
echo ========================================
        echo    ERRO NA CONFIGURAÇÃO DO BANCO ORACLE
echo ========================================
echo.
        echo Verifique:
        echo - Se o Oracle está rodando
        echo - Se as credenciais estão corretas
        echo - Se o SQL*Plus está disponível
        echo.
        echo Você pode configurar o banco manualmente mais tarde usando:
        echo setup-database.bat
    )
) else (
echo.
    echo Configuração do banco Oracle pulada.
    echo Você pode configurar manualmente mais tarde usando:
    echo setup-database.bat
)

echo.
echo ========================================
echo    CONFIGURAÇÃO FINALIZADA!
echo ========================================
echo.
echo ✅ JDK 17 configurado: %JDK_PATH%
echo ✅ Maven configurado e funcionando
echo ✅ Node.js configurado e funcionando
echo ✅ IntelliJ IDEA configurado automaticamente
echo ✅ Arquivos .gitignore criados
echo ✅ Documentação criada automaticamente
echo ✅ Dependências instaladas
echo.
echo 📚 Documentação criada:
echo - SOLUCAO_JDK_README.md (Solução do problema JDK)
echo - CONFIGURACAO_BANCO_README.md (Configuração do banco)
echo - CONFIGURACAO_APIS_BANCARIAS_README.md (APIs bancárias)
echo - RESOLUCAO_PROBLEMAS_README.md (Resolução de problemas)
echo.
echo Para iniciar os serviços:
echo 1. Backend: start-servicos.bat
echo 2. Frontend: cd frontend ^& npm start
echo.
echo Para parar os serviços:
echo parar-servicos.bat
echo.
echo Para verificar status:
echo status-servicos.bat
echo.
echo Se houver problemas:
echo - Frontend: reinstalar-frontend.bat
echo - Backend: resolver-backend.bat
echo.
echo Para abrir no IntelliJ IDEA:
echo 1. Abra o projeto
echo 2. O JDK 17 já está configurado automaticamente
echo 3. O projeto deve compilar sem erros
echo.
echo IMPORTANTE: Todos os arquivos de configuração foram criados automaticamente:
echo - Configurações do IntelliJ IDEA (.idea/)
echo - Configurações do Maven (.mvn/)
echo - Arquivos .gitignore (raiz, backend, frontend)
echo - Documentação completa
echo - Dependências instaladas localmente
echo.
echo Os arquivos de build e dependências foram excluídos do Git
echo mas mantidos localmente para desenvolvimento.
echo.
echo 🎉 AGORA VOCÊ TEM UM SETUP COMPLETAMENTE AUTOMATIZADO!
echo.
pause
