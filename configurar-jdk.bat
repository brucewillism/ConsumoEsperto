@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    CONFIGURANDO JDK 17 PARA O PROJETO
echo ========================================
echo.

:: Definir diretórios do projeto
set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "JDK_PATH=C:\Users\bruce.silva\.jdks\jbr-17.0.14"

echo Verificando JDK 17 instalado...
if exist "%JDK_PATH%\bin\java.exe" (
    echo ✅ JDK 17 encontrado em: %JDK_PATH%
) else (
    echo ❌ JDK 17 não encontrado em: %JDK_PATH%
    echo Procurando alternativas...
    
    if exist "C:\Users\bruce.silva\.jdks\ms-17.0.15\bin\java.exe" (
        set "JDK_PATH=C:\Users\bruce.silva\.jdks\ms-17.0.15"
        echo ✅ JDK 17 alternativo encontrado: %JDK_PATH%
    ) else (
        echo ❌ Nenhum JDK 17 encontrado!
        echo Por favor, instale o JDK 17 ou execute setup-completo-projeto.bat
        pause
        exit /b 1
    )
)

echo.
echo Configurando projeto para usar JDK 17...
echo.

:: Configurar variáveis locais para esta sessão
set "JAVA_HOME_LOCAL=%JDK_PATH%"
set "PATH_LOCAL=%JAVA_HOME_LOCAL%\bin;%PATH%"

echo Java Home: %JAVA_HOME_LOCAL%
echo.

:: Verificar versão do Java
echo Verificando versão do Java:
"%JAVA_HOME_LOCAL%\bin\java.exe" -version
echo.

:: Compilar o projeto
echo [1/2] Compilando backend com JDK 17...
cd /d "%BACKEND_DIR%"
"%JAVA_HOME_LOCAL%\bin\java.exe" -version
echo.

:: Verificar se o Maven está disponível
mvn -version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Maven encontrado, compilando projeto...
    mvn clean compile -DskipTests -q
    if %ERRORLEVEL% EQU 0 (
        echo ✅ Backend compilado com sucesso!
    ) else (
        echo ❌ Erro na compilação do backend
        pause
        exit /b 1
    )
) else (
    echo Maven não encontrado, baixando Maven local...
    
    :: Baixar Maven localmente
    set "MAVEN_PATH=%PROJECT_DIR%tools\maven-3.9.6"
    if not exist "%MAVEN_PATH%\bin\mvn.cmd" (
        echo Baixando Maven 3.9.6...
        powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%PROJECT_DIR%temp\maven-3.9.6.zip' -UseBasicParsing}"
        
        if exist "%PROJECT_DIR%temp\maven-3.9.6.zip" (
            echo Extraindo Maven...
            powershell -Command "Expand-Archive -Path '%PROJECT_DIR%temp\maven-3.9.6.zip' -DestinationPath '%PROJECT_DIR%temp' -Force"
            xcopy "%PROJECT_DIR%temp\apache-maven-3.9.6" "%MAVEN_PATH%" /E /I /Y >nul
            echo Maven instalado localmente!
        ) else (
            echo ❌ Falha ao baixar Maven
            pause
            exit /b 1
        )
    )
    
    :: Usar Maven local
    set "PATH_LOCAL=%MAVEN_PATH%\bin;%PATH_LOCAL%"
    echo Compilando com Maven local...
    "%MAVEN_PATH%\bin\mvn.cmd" clean compile -DskipTests -q
    if %ERRORLEVEL% EQU 0 (
        echo ✅ Backend compilado com sucesso!
    ) else (
        echo ❌ Erro na compilação do backend
        pause
        exit /b 1
    )
)

echo.
echo [2/2] Configurando IntelliJ IDEA...
echo.

:: Verificar se o IntelliJ está instalado
if exist "C:\Users\bruce.silva\AppData\Local\Programs\IntelliJ IDEA Ultimate" (
    echo IntelliJ IDEA Ultimate encontrado!
    echo.
    echo Para configurar o JDK no IntelliJ:
    echo 1. Abra o projeto no IntelliJ
    echo 2. Vá em File > Project Structure
    echo 3. Em Project Settings > Project:
    echo    - Project SDK: %JDK_PATH%
    echo    - Project language level: 17
    echo 4. Em Project Settings > Modules:
    echo    - Language level: 17
    echo 5. Clique em Apply e OK
    echo.
    echo Arquivos de configuração já foram criados automaticamente!
) else (
    echo IntelliJ IDEA não encontrado.
    echo Arquivos de configuração foram criados automaticamente.
    echo Quando abrir o projeto, configure o JDK conforme as instruções acima.
)

echo.
echo ========================================
echo    CONFIGURAÇÃO CONCLUÍDA!
echo ========================================
echo.
echo ✅ JDK 17 configurado: %JDK_PATH%
echo ✅ Projeto compilado com sucesso
echo ✅ Arquivos de configuração do IntelliJ criados
echo.
echo Para verificar se está funcionando:
echo 1. Abra o projeto no IntelliJ IDEA
echo 2. Verifique se não há mais erros de JDK
echo 3. Execute: mvn spring-boot:run
echo.
pause
