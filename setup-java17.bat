@echo off
REM =============================================================================
REM CONFIGURADOR JAVA 17 PARA PROJETO CONSUMO ESPERTO
REM =============================================================================
REM Este script configura o Java 17 da pasta tools apenas para este projeto
REM =============================================================================

echo Configurando Java 17 para o projeto ConsumoEsperto...
echo.

REM Verificar se a pasta tools existe
if not exist "..\tools\java\ms-17.0.15" (
    echo ERRO: Pasta tools\java\ms-17.0.15 nao encontrada!
    echo Execute primeiro o script de instalacao de ferramentas.
    pause
    exit /b 1
)

REM Configurar JAVA_HOME para este projeto
set JAVA_HOME=%~dp0..\tools\java\ms-17.0.15
echo JAVA_HOME configurado: %JAVA_HOME%

REM Adicionar Java ao PATH temporariamente
set PATH=%JAVA_HOME%\bin;%PATH%
echo PATH atualizado com Java 17

REM Verificar versao do Java
echo.
echo Verificando versao do Java...
"%JAVA_HOME%\bin\java.exe" -version

echo.
echo =============================================================================
echo CONFIGURACAO CONCLUIDA!
echo =============================================================================
echo.
echo Agora voce pode executar:
echo   mvn clean compile
echo   mvn spring-boot:run
echo.
echo NOTA: Esta configuracao e valida apenas para esta janela de comando.
echo Para uma nova janela, execute este script novamente.
echo =============================================================================
echo.

REM Manter a janela aberta
cmd /k
