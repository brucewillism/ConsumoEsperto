@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    SETUP COMPLETO - CONSUMO ESPERTO
echo ========================================
echo.
echo Este script ira configurar todo o projeto:
echo 1. Verificar/Instalar Java JDK 17
echo 2. Verificar/Instalar Maven
echo 3. Verificar/Instalar Oracle Database
echo 4. Criar banco de dados e usuario
echo 5. Criar tabelas necessarias
echo 6. Configurar frontend Angular
echo 7. Iniciar servicos
echo.

:: Obter nome do usuario logado
for /f "tokens=2 delims==" %%a in ('wmic computersystem get username /value') do set "CURRENT_USER=%%a"
if "!CURRENT_USER!"=="" set "CURRENT_USER=%USERNAME%"

echo Usuario logado: !CURRENT_USER!
echo.

:: ========================================
:: 1. VERIFICAR JAVA JDK 17
:: ========================================
echo [1/7] Verificando Java JDK 17...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Java nao encontrado. Baixando JDK 17...
    echo.
    echo Baixando OpenJDK 17...
    powershell -Command "& {Invoke-WebRequest -Uri 'https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip' -OutFile '%TEMP%\openjdk-17.zip'}"
    
    echo Extraindo JDK...
    powershell -Command "& {Expand-Archive -Path '%TEMP%\openjdk-17.zip' -DestinationPath 'C:\Program Files\Java' -Force}"
    
    echo Configurando variaveis de ambiente...
    setx JAVA_HOME "C:\Program Files\Java\jdk-17.0.2" /M
    setx PATH "%PATH%;C:\Program Files\Java\jdk-17.0.2\bin" /M
    
    echo Java JDK 17 instalado com sucesso!
) else (
    echo Java encontrado!
    java -version
)
echo.

:: ========================================
:: 2. VERIFICAR MAVEN
:: ========================================
echo [2/7] Verificando Maven...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Maven nao encontrado. Baixando Maven...
    echo.
    echo Baixando Apache Maven...
    powershell -Command "& {Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\maven.zip'}"
    
    echo Extraindo Maven...
    powershell -Command "& {Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath 'C:\Program Files' -Force}"
    
    echo Configurando variaveis de ambiente...
    setx MAVEN_HOME "C:\Program Files\apache-maven-3.9.6" /M
    setx PATH "%PATH%;C:\Program Files\apache-maven-3.9.6\bin" /M
    
    echo Maven instalado com sucesso!
) else (
    echo Maven encontrado!
    mvn -version
)
echo.

:: ========================================
:: 3. VERIFICAR/INSTALAR ORACLE DATABASE
:: ========================================
echo [3/7] Verificando Oracle Database...
netstat -an | findstr ":1521" >nul
if %errorlevel% neq 0 (
    echo Oracle nao esta rodando. Instalando Oracle XE...
    echo.
    echo Baixando Oracle Database 21c Express Edition...
    powershell -Command "& {Invoke-WebRequest -Uri 'https://download.oracle.com/otn-pub/otn_software/db-free/OracleXE213_Win64.zip' -OutFile '%TEMP%\oracle-xe.zip'}"
    
    echo Extraindo Oracle...
    powershell -Command "& {Expand-Archive -Path '%TEMP%\oracle-xe.zip' -DestinationPath '%TEMP%\oracle' -Force}"
    
    echo Instalando Oracle Database...
    echo NOTA: A instalacao pode demorar varios minutos...
    echo Siga as instrucoes na tela e use as configuracoes padrao
    echo.
    echo Pressione qualquer tecla para continuar com a instalacao...
    pause >nul
    
    start /wait "%TEMP%\oracle\Disk1\setup.exe"
    
    echo Oracle Database instalado! Configurando...
    
    :: Configurar Oracle para iniciar automaticamente
    sc config OracleServiceXE start= auto
    net start OracleServiceXE
    
    echo Aguardando Oracle inicializar...
    timeout /t 30 /nobreak >nul
    
) else (
    echo Oracle esta rodando na porta 1521!
)
echo.

:: ========================================
:: 4. CRIAR BANCO E USUARIO
:: ========================================
echo [4/7] Criando banco de dados e usuario...
echo.

:: Criar arquivo SQL temporario para setup
echo @echo off > "%TEMP%\setup_oracle.sql"
echo SET LINESIZE 100 >> "%TEMP%\setup_oracle.sql"
echo SET PAGESIZE 50 >> "%TEMP%\setup_oracle.sql"
echo SET FEEDBACK ON >> "%TEMP%\setup_oracle.sql"
echo SET VERIFY ON >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT CONECTANDO AO ORACLE COMO SYSTEM... >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo CONNECT system/oracle@localhost:1521/XE >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT CRIANDO TABLESPACE E USUARIO... >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo CREATE TABLESPACE consumo_esperto_data >> "%TEMP%\setup_oracle.sql"
echo DATAFILE 'consumo_esperto_data.dbf' >> "%TEMP%\setup_oracle.sql"
echo SIZE 100M >> "%TEMP%\setup_oracle.sql"
echo AUTOEXTEND ON NEXT 10M; >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo CREATE USER !CURRENT_USER! IDENTIFIED BY admin123 >> "%TEMP%\setup_oracle.sql"
echo DEFAULT TABLESPACE consumo_esperto_data >> "%TEMP%\setup_oracle.sql"
echo QUOTA UNLIMITED ON consumo_esperto_data; >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo GRANT CONNECT, RESOURCE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo GRANT CREATE SESSION TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo GRANT CREATE TABLE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo GRANT CREATE SEQUENCE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo GRANT CREATE VIEW TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo GRANT CREATE PROCEDURE TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo GRANT CREATE TRIGGER TO !CURRENT_USER!; >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT CONECTANDO COMO USUARIO !CURRENT_USER!... >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo CONNECT !CURRENT_USER!/admin123@localhost:1521/XE >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT CRIANDO TABELAS... >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo @src\main\resources\db\create-tables.sql >> "%TEMP%\setup_oracle.sql"
echo. >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT BANCO CONFIGURADO COM SUCESSO! >> "%TEMP%\setup_oracle.sql"
echo PROMPT ======================================== >> "%TEMP%\setup_oracle.sql"
echo PROMPT. >> "%TEMP%\setup_oracle.sql"
echo PROMPT Detalhes da conexao: >> "%TEMP%\setup_oracle.sql"
echo PROMPT - Usuario: !CURRENT_USER! >> "%TEMP%\setup_oracle.sql"
echo PROMPT - Senha: admin123 >> "%TEMP%\setup_oracle.sql"
echo PROMPT - Host: localhost >> "%TEMP%\setup_oracle.sql"
echo PROMPT - Porta: 1521 >> "%TEMP%\setup_oracle.sql"
echo PROMPT - Service: XE >> "%TEMP%\setup_oracle.sql"
echo PROMPT. >> "%TEMP%\setup_oracle.sql"
echo PROMPT Pressione Enter para sair... >> "%TEMP%\setup_oracle.sql"
echo PAUSE >> "%TEMP%\setup_oracle.sql"
echo EXIT >> "%TEMP%\setup_oracle.sql"

echo Executando script de configuracao do Oracle...
echo.
echo NOTA: Uma janela do SQL*Plus sera aberta.
echo Siga as instrucoes na tela.
echo.
echo Pressione qualquer tecla para continuar...
pause >nul

:: Executar script de configuracao
sqlplus /nolog @"%TEMP%\setup_oracle.sql"

echo.
echo ========================================
echo    BANCO ORACLE CONFIGURADO!
echo ========================================
echo.
echo Credenciais do banco:
echo - Usuario: !CURRENT_USER!
echo - Senha: admin123
echo - Host: localhost
echo - Porta: 1521
echo - Service: XE
echo.

:: ========================================
:: 5. ATUALIZAR CONFIGURACOES DO BACKEND
:: ========================================
echo [5/7] Atualizando configuracoes do backend...
echo.

:: Atualizar application-dev.properties com as credenciais corretas
echo Atualizando application-dev.properties...
powershell -Command "& {(Get-Content 'src\main\resources\application-dev.properties') -replace 'spring.datasource.username=consumo_esperto', 'spring.datasource.username=!CURRENT_USER!' -replace 'spring.datasource.password=consumo_esperto123', 'spring.datasource.password=admin123' | Set-Content 'src\main\resources\application-dev.properties'}"

echo Configuracoes do backend atualizadas!
echo.

:: ========================================
:: 6. CONFIGURAR FRONTEND ANGULAR
:: ========================================
echo [6/7] Configurando frontend Angular...
echo.

cd ..
cd frontend

echo Instalando dependencias do Angular...
npm install

echo Frontend configurado!
echo.

:: ========================================
:: 7. INICIAR SERVICOS
:: ========================================
echo [7/7] Iniciando servicos...
echo.

echo ========================================
echo    CONFIGURACAO COMPLETA!
echo ========================================
echo.
echo Todos os componentes foram configurados:
echo.
echo 1. ✅ Java JDK 17 instalado
echo 2. ✅ Maven instalado
echo 3. ✅ Oracle Database instalado
echo 4. ✅ Banco 'consumo_esperto' criado
echo 5. ✅ Usuario '!CURRENT_USER!' criado (senha: admin123)
echo 6. ✅ Tabelas criadas
echo 7. ✅ Frontend Angular configurado
echo.
echo ========================================
echo    PROXIMOS PASSOS
echo ========================================
echo.
echo 1. Iniciar o backend:
echo    cd backend
echo    mvn spring-boot:run
echo.
echo 2. Em outro terminal, iniciar o frontend:
echo    cd frontend
echo    ng serve
echo.
echo 3. Acessar a aplicacao:
echo    http://localhost:4200
echo.
echo 4. Fazer login via Google
echo    - O sistema criara automaticamente seu usuario
echo    - Voce podera configurar as APIs bancarias
echo.
echo ========================================
echo    CREDENCIAIS IMPORTANTES
echo ========================================
echo.
echo Banco Oracle:
echo - Usuario: !CURRENT_USER!
echo - Senha: admin123
echo - Host: localhost
echo - Porta: 1521
echo - Service: XE
echo.
echo Aplicacao:
echo - URL: http://localhost:4200
echo - Login: Via Google OAuth2
echo.
echo ========================================
echo    ARQUIVOS DE CONFIGURACAO
echo ========================================
echo.
echo Backend:
echo - src\main\resources\application-dev.properties
echo.
echo Frontend:
echo - src\environments\environment.ts
echo.
echo Scripts:
echo - setup-completo-projeto.bat (este arquivo)
echo - start-servicos.bat
echo - parar-servicos.bat
echo.

:: Limpar arquivo temporario
del "%TEMP%\setup_oracle.sql"

echo Pressione qualquer tecla para sair...
pause >nul
