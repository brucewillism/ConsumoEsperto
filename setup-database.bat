@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    SETUP DO BANCO DE DADOS ORACLE
echo    CONSUMO ESPERTO
echo ========================================
echo.

:: Definir diretórios do projeto
set "PROJECT_DIR=%~dp0"
set "DB_DIR=%PROJECT_DIR%backend\src\main\resources\db"

echo Este script irá configurar o banco de dados Oracle
echo para o projeto ConsumoEsperto.
echo.
echo Pré-requisitos:
echo - Oracle Database instalado e rodando
echo - Usuário SYSTEM ou SYS com privilégios de administrador
echo - SQL*Plus disponível no PATH
echo.

:: Verificar se SQL*Plus está disponível
echo Verificando SQL*Plus...
sqlplus -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo ERRO: SQL*Plus não encontrado no PATH!
    echo.
    echo Por favor, instale o Oracle Client ou adicione ao PATH.
    echo.
    echo Alternativas:
    echo 1. Instalar Oracle Database Express Edition (XE)
    echo 2. Instalar Oracle Instant Client
    echo 3. Adicionar o diretório do Oracle ao PATH
    echo.
    echo Exemplo de PATH:
    echo C:\oracle\product\12.2.0\client_1\bin
    echo.
    pause
    exit /b 1
)

echo SQL*Plus encontrado! Continuando...
echo.

:: Solicitar credenciais
echo Por favor, forneça as credenciais do Oracle:
echo.
set /p "ORACLE_USER=Usuário (SYSTEM/SYS): "
set /p "ORACLE_PASSWORD=Senha: "
set /p "ORACLE_HOST=Host (localhost): "
set /p "ORACLE_PORT=Porta (1521): "
set /p "ORACLE_SERVICE=Service (XE/ORCL): "

if "!ORACLE_HOST!"=="" set "ORACLE_HOST=localhost"
if "!ORACLE_PORT!"=="" set "ORACLE_PORT=1521"
if "!ORACLE_SERVICE!"=="" set "ORACLE_SERVICE=XE"

echo.
echo ========================================
echo    RESUMO DA CONFIGURAÇÃO
echo ========================================
echo.
echo Host: !ORACLE_HOST!
echo Porta: !ORACLE_PORT!
echo Service: !ORACLE_SERVICE!
echo Usuário Oracle: !ORACLE_USER!
echo.
echo Banco a ser criado:
echo - Nome: consumo_esperto
echo - Usuário: consumo_esperto
echo - Senha: consumo_esperto123
echo.
echo Tabelas a serem criadas:
echo - usuarios, categorias, cartoes_credito
echo - transacoes, faturas, compras_parceladas
echo - parcelas, autorizacoes_bancarias
echo - bank_api_configs
echo.
echo Deseja continuar? (S/N)
set /p "CONTINUE="

if /i not "!CONTINUE!"=="S" (
    echo.
    echo Operação cancelada pelo usuário.
    pause
    exit /b 0
)

echo.
echo ========================================
echo    CRIANDO BANCO DE DADOS...
echo ========================================
echo.

:: Criar arquivo de conexão temporário
echo @echo off > "%TEMP%\create_db.sql"
echo SET LINESIZE 100 >> "%TEMP%\create_db.sql"
echo SET PAGESIZE 50 >> "%TEMP%\create_db.sql"
echo SET FEEDBACK ON >> "%TEMP%\create_db.sql"
echo SET VERIFY ON >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT CONECTANDO AO ORACLE... >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo CONNECT !ORACLE_USER!/!ORACLE_PASSWORD!@!ORACLE_HOST!:!ORACLE_PORT!/!ORACLE_SERVICE! >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT CRIANDO TABLESPACE E USUÁRIO... >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo @%DB_DIR%\init-oracle.sql >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT CONECTANDO COMO USUÁRIO CONSUMO_ESPERTO... >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo CONNECT consumo_esperto/consumo_esperto123@!ORACLE_HOST!:!ORACLE_PORT!/!ORACLE_SERVICE! >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT CRIANDO TABELAS... >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo @%DB_DIR%\create-tables.sql >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT INSERINDO DADOS DE TESTE... >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo @%DB_DIR%\insert-test-data.sql >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT VERIFICANDO TABELAS CRIADAS... >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo SELECT table_name, tablespace_name FROM user_tables ORDER BY table_name; >> "%TEMP%\create_db.sql"
echo. >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT BANCO DE DADOS CRIADO COM SUCESSO! >> "%TEMP%\create_db.sql"
echo PROMPT ======================================== >> "%TEMP%\create_db.sql"
echo PROMPT. >> "%TEMP%\create_db.sql"
echo PROMPT Detalhes da conexão: >> "%TEMP%\create_db.sql"
echo PROMPT - Usuário: consumo_esperto >> "%TEMP%\create_db.sql"
echo PROMPT - Senha: consumo_esperto123 >> "%TEMP%\create_db.sql"
echo PROMPT - Host: !ORACLE_HOST! >> "%TEMP%\create_db.sql"
echo PROMPT - Porta: !ORACLE_PORT! >> "%TEMP%\create_db.sql"
echo PROMPT - Service: !ORACLE_SERVICE! >> "%TEMP%\create_db.sql"
echo PROMPT. >> "%TEMP%\create_db.sql"
echo PROMPT Pressione Enter para sair... >> "%TEMP%\create_db.sql"
echo PAUSE >> "%TEMP%\create_db.sql"
echo EXIT >> "%TEMP%\create_db.sql"

echo Executando script de criação do banco...
echo.
echo NOTA: Uma janela do SQL*Plus será aberta.
echo Siga as instruções na tela.
echo.
echo Pressione qualquer tecla para continuar...
pause >nul

:: Executar script de criação
sqlplus /nolog @"%TEMP%\create_db.sql"

echo.
echo ========================================
echo    CONFIGURAÇÃO COMPLETA!
echo ========================================
echo.
echo Se tudo foi executado com sucesso:
echo.
echo 1. Banco de dados 'consumo_esperto' criado
echo 2. Usuário 'consumo_esperto' criado
echo 3. Todas as tabelas criadas
echo 4. Dados de teste inseridos
echo.
echo Para verificar o banco:
echo - Conecte via SQL*Plus: sqlplus consumo_esperto/consumo_esperto123@localhost:1521/XE
echo - Execute: SELECT table_name FROM user_tables;
echo.
echo Para conectar via aplicação:
echo - Configure o application.properties com as credenciais:
echo   spring.datasource.url=jdbc:oracle:thin:@localhost:1521:XE
echo   spring.datasource.username=consumo_esperto
echo   spring.datasource.password=consumo_esperto123
echo.
echo ========================================
echo    ARQUIVOS DE SCRIPT:
echo ========================================
echo.
echo Scripts utilizados:
echo - %DB_DIR%\init-oracle.sql (cria tablespace e usuário)
echo - %DB_DIR%\create-tables.sql (cria todas as tabelas)
echo - %DB_DIR%\insert-test-data.sql (insere dados de teste)
echo.
echo Para executar manualmente:
echo 1. Conecte como SYSTEM/SYS
echo 2. Execute: @%DB_DIR%\init-oracle.sql
echo 3. Conecte como consumo_esperto
echo 4. Execute: @%DB_DIR%\create-tables.sql
echo 5. Execute: @%DB_DIR%\insert-test-data.sql
echo.

:: Limpar arquivo temporário
del "%TEMP%\create_db.sql"

pause
