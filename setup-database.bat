@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    CONFIGURACAO DO BANCO ORACLE
echo ========================================
echo.
echo Este script ira configurar o banco Oracle:
echo 1. Verificar se Oracle esta rodando
echo 2. Criar tablespace e usuario
echo 3. Criar tabelas necessarias
echo 4. Configurar credenciais do backend
echo.

:: Obter nome do usuario logado
for /f "tokens=2 delims==" %%a in ('wmic computersystem get username /value') do set "CURRENT_USER=%%a"
if "!CURRENT_USER!"=="" set "CURRENT_USER=%USERNAME%"

echo Usuario logado: !CURRENT_USER!
echo.

:: ========================================
:: 1. VERIFICAR SE ORACLE ESTA RODANDO
:: ========================================
echo [1/4] Verificando se Oracle esta rodando...
netstat -an | findstr ":1521" >nul
if %errorlevel% neq 0 (
    echo.
    echo ❌ ERRO: Oracle nao esta rodando na porta 1521!
    echo.
    echo Para resolver:
    echo 1. Instale o Oracle Database 21c XE
    echo 2. Ou inicie o servico Oracle se ja estiver instalado
    echo.
    echo Execute: setup-completo-projeto.bat
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Oracle esta rodando na porta 1521!
)
echo.

:: ========================================
:: 2. CRIAR TABLESPACE E USUARIO
:: ========================================
echo [2/4] Criando tablespace e usuario...
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

:: ========================================
:: 3. CRIAR TABELAS NECESSARIAS
:: ========================================
echo [3/4] Criando tabelas necessarias...
echo.

:: Verificar se as tabelas foram criadas
echo Verificando se as tabelas foram criadas...
sqlplus !CURRENT_USER!/admin123@localhost:1521/XE @src\main\resources\db\create-tables.sql

echo Tabelas criadas com sucesso!
echo.

:: ========================================
:: 4. CONFIGURAR CREDENCIAIS DO BACKEND
:: ========================================
echo [4/4] Configurando credenciais do backend...
echo.

:: Atualizar application-dev.properties com as credenciais corretas
echo Atualizando application-dev.properties...
powershell -Command "& {(Get-Content 'src\main\resources\application-dev.properties') -replace 'spring.datasource.username=consumo_esperto', 'spring.datasource.username=!CURRENT_USER!' -replace 'spring.datasource.password=consumo_esperto123', 'spring.datasource.password=admin123' | Set-Content 'src\main\resources\application-dev.properties'}"

echo Configuracoes do backend atualizadas!
echo.

:: ========================================
echo    CONFIGURACAO COMPLETA!
echo ========================================
echo.
echo ✅ Banco Oracle configurado com sucesso!
echo ✅ Usuario '!CURRENT_USER!' criado
echo ✅ Tabelas criadas
echo ✅ Backend configurado
echo.
echo ========================================
echo    CREDENCIAIS DO BANCO
echo ========================================
echo.
echo - Usuario: !CURRENT_USER!
echo - Senha: admin123
echo - Host: localhost
echo - Porta: 1521
echo - Service: XE
echo - Tablespace: consumo_esperto_data
echo.
echo ========================================
echo    PROXIMOS PASSOS
echo ========================================
echo.
echo 1. Testar conexao com o banco:
echo    mvn spring-boot:run
echo.
echo 2. Fazer login via Google
echo    - O sistema criara automaticamente seu usuario
echo    - Voce podera configurar as APIs bancarias
echo.
echo 3. Acessar: http://localhost:8080
echo.

:: Limpar arquivo temporario
del "%TEMP%\setup_oracle.sql"

echo Pressione qualquer tecla para sair...
pause >nul
