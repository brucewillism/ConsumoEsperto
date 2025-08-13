@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    TESTE DAS FERRAMENTAS LOCAIS
echo ========================================
echo.

:: Verificar se a pasta tools existe
if not exist "tools" (
    echo ERRO: Pasta 'tools' nao encontrada!
    echo Execute setup-completo-projeto.bat primeiro
    pause
    exit /b 1
)

echo Pasta 'tools' encontrada!
echo.

:: Testar Java
echo [1/3] Testando Java JDK 17.0.2...
if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    set "JAVA_HOME=%CD%\tools\java\jdk-17.0.2"
    set "PATH=%CD%\tools\java\jdk-17.0.2\bin;%PATH%"
    echo ✅ Java encontrado: !JAVA_HOME!
    
    echo Testando versao do Java...
    java -version
    if %errorlevel% equ 0 (
        echo ✅ Java funcionando corretamente!
    ) else (
        echo ❌ Java nao esta funcionando!
    )
) else (
    echo ❌ Java JDK 17.0.2 nao encontrado!
)
echo.

:: Testar Maven
echo [2/3] Testando Maven 3.9.6...
if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MAVEN_HOME=%CD%\tools\maven\apache-maven-3.9.6"
    set "PATH=%CD%\tools\maven\apache-maven-3.9.6\bin;%PATH%"
    echo ✅ Maven encontrado: !MAVEN_HOME!
    
    echo Testando versao do Maven...
    mvn -version
    if %errorlevel% equ 0 (
        echo ✅ Maven funcionando corretamente!
    ) else (
        echo ❌ Maven nao esta funcionando!
    )
) else (
    echo ❌ Maven 3.9.6 nao encontrado!
)
echo.

:: Testar Node.js
echo [3/3] Testando Node.js 18.19.0...
if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
    set "NODE_HOME=%CD%\tools\node\node-v18.19.0-win-x64"
    set "PATH=%CD%\tools\node\node-v18.19.0-win-x64;%PATH%"
    echo ✅ Node.js encontrado: !NODE_HOME!
    
    echo Testando versao do Node.js...
    node --version
    if %errorlevel% equ 0 (
        echo ✅ Node.js funcionando corretamente!
    ) else (
        echo ❌ Node.js nao esta funcionando!
    )
    
    echo Testando versao do npm...
    npm --version
    if %errorlevel% equ 0 (
        echo ✅ npm funcionando corretamente!
    ) else (
        echo ❌ npm nao esta funcionando!
    )
) else (
    echo ❌ Node.js 18.19.0 nao encontrado!
)
echo.

:: Resumo final
echo ========================================
echo    RESUMO DOS TESTES
echo ========================================
echo.

if exist "tools\java\jdk-17.0.2\bin\java.exe" (
    echo ✅ Java: OK
) else (
    echo ❌ Java: FALHOU
)

if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" (
    echo ✅ Maven: OK
) else (
    echo ❌ Maven: FALHOU
)

if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
    echo ✅ Node.js: OK
) else (
    echo ❌ Node.js: FALHOU
)

echo.
if exist "tools\java\jdk-17.0.2\bin\java.exe" if exist "tools\maven\apache-maven-3.9.6\bin\mvn.cmd" if exist "tools\node\node-v18.19.0-win-x64\node.exe" (
    echo 🎉 TODAS AS FERRAMENTAS ESTAO FUNCIONANDO!
    echo.
    echo Voce pode agora executar:
    echo - start-servicos.bat para iniciar os servicos
    echo.
) else (
    echo ⚠️  ALGUMAS FERRAMENTAS FALHARAM!
    echo.
    echo Execute setup-completo-projeto.bat novamente
    echo.
)

echo Pressione qualquer tecla para sair...
pause >nul
