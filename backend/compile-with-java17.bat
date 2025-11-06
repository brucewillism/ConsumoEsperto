@echo off
echo Configurando Java 17 da pasta tools...
set "JAVA_HOME=%~dp0..\tools\java\ms-17.0.15"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo JAVA_HOME configurado: %JAVA_HOME%
echo.

echo Verificando versao do Java...
"%JAVA_HOME%\bin\java" -version
echo.

echo Compilando o projeto...
mvn clean compile
echo.

echo Compilacao concluida!
pause
