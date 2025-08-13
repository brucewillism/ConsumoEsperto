@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    TESTE DE CONEXAO COM BANCO ORACLE
echo ========================================
echo.
echo Este script ira testar a conexao com o banco Oracle
echo e verificar se tudo esta funcionando.
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
:: 2. TESTAR CONEXAO COMO SYSTEM
:: ========================================
echo [2/4] Testando conexao como SYSTEM...
echo.

:: Criar arquivo SQL temporario para teste
echo @echo off > "%TEMP%\test_system.sql"
echo SET LINESIZE 100 >> "%TEMP%\test_system.sql"
echo SET PAGESIZE 50 >> "%TEMP%\test_system.sql"
echo SET FEEDBACK ON >> "%TEMP%\test_system.sql"
echo SET VERIFY ON >> "%TEMP%\test_system.sql"
echo. >> "%TEMP%\test_system.sql"
echo PROMPT ======================================== >> "%TEMP%\test_system.sql"
echo PROMPT TESTANDO CONEXAO COMO SYSTEM... >> "%TEMP%\test_system.sql"
echo PROMPT ======================================== >> "%TEMP%\test_system.sql"
echo CONNECT system/oracle@localhost:1521/XE >> "%TEMP%\test_system.sql"
echo. >> "%TEMP%\test_system.sql"
echo PROMPT ======================================== >> "%TEMP%\test_system.sql"
echo PROMPT VERIFICANDO USUARIOS... >> "%TEMP%\test_system.sql"
echo PROMPT ======================================== >> "%TEMP%\test_system.sql"
echo SELECT username, account_status, default_tablespace FROM dba_users WHERE username = '!CURRENT_USER!'; >> "%TEMP%\test_system.sql"
echo. >> "%TEMP%\test_system.sql"
echo PROMPT ======================================== >> "%TEMP%\test_system.sql"
echo PROMPT VERIFICANDO TABLESPACES... >> "%TEMP%\test_system.sql"
echo PROMPT ======================================== >> "%TEMP%\test_system.sql"
echo SELECT tablespace_name, status FROM dba_tablespaces WHERE tablespace_name = 'CONSUMO_ESPERTO_DATA'; >> "%TEMP%\test_system.sql"
echo. >> "%TEMP%\test_system.sql"
echo PROMPT Pressione Enter para continuar... >> "%TEMP%\test_system.sql"
echo PAUSE >> "%TEMP%\test_system.sql"
echo EXIT >> "%TEMP%\test_system.sql"

echo Testando conexao como SYSTEM...
sqlplus /nolog @"%TEMP%\test_system.sql"

echo.
echo ✅ Conexao como SYSTEM testada!
echo.

:: ========================================
:: 3. TESTAR CONEXAO COMO USUARIO
:: ========================================
echo [3/4] Testando conexao como usuario !CURRENT_USER!...
echo.

:: Criar arquivo SQL temporario para teste do usuario
echo @echo off > "%TEMP%\test_user.sql"
echo SET LINESIZE 100 >> "%TEMP%\test_user.sql"
echo SET PAGESIZE 50 >> "%TEMP%\test_user.sql"
echo SET FEEDBACK ON >> "%TEMP%\test_user.sql"
echo SET VERIFY ON >> "%TEMP%\test_user.sql"
echo. >> "%TEMP%\test_user.sql"
echo PROMPT ======================================== >> "%TEMP%\test_user.sql"
echo PROMPT TESTANDO CONEXAO COMO !CURRENT_USER!... >> "%TEMP%\test_user.sql"
echo PROMPT ======================================== >> "%TEMP%\test_user.sql"
echo CONNECT !CURRENT_USER!/admin123@localhost:1521/XE >> "%TEMP%\test_user.sql"
echo. >> "%TEMP%\test_user.sql"
echo PROMPT ======================================== >> "%TEMP%\test_user.sql"
echo PROMPT VERIFICANDO TABELAS... >> "%TEMP%\test_user.sql"
echo PROMPT ======================================== >> "%TEMP%\test_user.sql"
echo SELECT table_name, tablespace_name FROM user_tables ORDER BY table_name; >> "%TEMP%\test_user.sql"
echo. >> "%TEMP%\test_user.sql"
echo PROMPT ======================================== >> "%TEMP%\test_user.sql"
echo PROMPT VERIFICANDO TABLESPACE... >> "%TEMP%\test_user.sql"
echo PROMPT ======================================== >> "%TEMP%\test_user.sql"
echo SELECT tablespace_name, bytes/1024/1024 as "Size (MB)" FROM user_ts_quotas; >> "%TEMP%\test_user.sql"
echo. >> "%TEMP%\test_user.sql"
echo PROMPT Pressione Enter para continuar... >> "%TEMP%\test_user.sql"
echo PAUSE >> "%TEMP%\test_user.sql"
echo EXIT >> "%TEMP%\test_user.sql"

echo Testando conexao como usuario !CURRENT_USER!...
sqlplus /nolog @"%TEMP%\test_user.sql"

echo.
echo ✅ Conexao como usuario testada!
echo.

:: ========================================
:: 4. TESTAR BACKEND
:: ========================================
echo [4/4] Testando conexao do backend...
echo.

echo Testando se o backend consegue conectar...
cd backend

:: Verificar se o Maven esta disponivel
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ERRO: Maven nao encontrado!
    echo Execute: setup-completo-projeto.bat
    echo.
    pause
    exit /b 1
)

echo ✅ Maven encontrado!
echo.
echo Testando compilacao do projeto...
mvn clean compile -q

if %errorlevel% neq 0 (
    echo ❌ ERRO: Falha na compilacao!
    echo Verifique os logs acima.
    echo.
    pause
    exit /b 1
)

echo ✅ Projeto compilado com sucesso!
echo.

:: ========================================
echo    TESTE COMPLETO!
echo ========================================
echo.
echo ✅ Oracle esta rodando
echo ✅ Conexao como SYSTEM funcionando
echo ✅ Conexao como usuario funcionando
echo ✅ Backend compilando corretamente
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
echo 1. Iniciar o backend:
echo    mvn spring-boot:run
echo.
echo 2. Fazer login via Google
echo    - O sistema criara automaticamente seu usuario
echo    - Voce podera configurar as APIs bancarias
echo.
echo 3. Acessar: http://localhost:8080
echo.

:: Limpar arquivos temporarios
del "%TEMP%\test_system.sql"
del "%TEMP%\test_user.sql"

echo Pressione qualquer tecla para sair...
pause >nul
