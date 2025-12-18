@echo off
REM =============================================================================
REM Script para configurar JAVA_HOME para o projeto ConsumoEsperto
REM =============================================================================
REM Este script configura a variável JAVA_HOME para apontar para a instalação
REM do Java na pasta tools/java/ms-17.0.15
REM =============================================================================

echo.
echo =============================================================================
echo Configurando JAVA_HOME para ConsumoEsperto
echo =============================================================================
echo.

REM Obtém o diretório atual do script
set SCRIPT_DIR=%~dp0
set JAVA_HOME_LOCAL=%SCRIPT_DIR%tools\java\ms-17.0.15

REM Verifica se o diretório Java existe
if not exist "%JAVA_HOME_LOCAL%" (
    echo [ERRO] Diretório Java não encontrado: %JAVA_HOME_LOCAL%
    echo.
    echo Por favor, verifique se o Java está instalado na pasta tools/java/
    echo.
    pause
    exit /b 1
)

REM Verifica se java.exe existe
if not exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
    echo [ERRO] java.exe não encontrado em: %JAVA_HOME_LOCAL%\bin\
    echo.
    echo Por favor, verifique se a instalação do Java está completa.
    echo.
    pause
    exit /b 1
)

REM Configura JAVA_HOME para esta sessão
set JAVA_HOME=%JAVA_HOME_LOCAL%
set PATH=%JAVA_HOME%\bin;%PATH%

echo [OK] JAVA_HOME configurado: %JAVA_HOME%
echo [OK] Java adicionado ao PATH
echo.

REM Verifica a versão do Java
echo Verificando versão do Java...
"%JAVA_HOME%\bin\java.exe" -version
echo.

REM Cria arquivo .env para uso em IDEs
echo Criando arquivo .env para configuração do IDE...
(
    echo # Configuração JAVA_HOME para ConsumoEsperto
    echo # Gerado automaticamente por configurar-java-home.bat
    echo JAVA_HOME=%JAVA_HOME_LOCAL%
) > .env
echo [OK] Arquivo .env criado
echo.

REM Instruções para configuração permanente
echo =============================================================================
echo IMPORTANTE: Configuração da Sessão Atual
echo =============================================================================
echo.
echo A variável JAVA_HOME foi configurada APENAS para esta sessão do terminal.
echo.
echo Para configurar permanentemente:
echo.
echo 1. Abra "Variáveis de Ambiente" do Windows:
echo    - Pressione Win + R
echo    - Digite: sysdm.cpl
echo    - Clique em "Variáveis de Ambiente"
echo.
echo 2. Adicione ou edite a variável JAVA_HOME:
echo    Nome: JAVA_HOME
echo    Valor: %JAVA_HOME_LOCAL%
echo.
echo 3. Adicione ao PATH:
echo    Adicione: %%JAVA_HOME%%\bin
echo.
echo =============================================================================
echo.
echo Configuração concluída! Você pode usar o Java agora.
echo.
pause

