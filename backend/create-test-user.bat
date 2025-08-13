@echo off
echo ========================================
echo    CRIANDO USUARIO DE TESTE
echo ========================================
echo.
echo Este script criara um usuario de teste no banco:
echo - Username: teste
echo - Email: teste@teste.com
echo - Senha: 123456
echo.

:: Verificar se o Oracle esta rodando
echo Verificando se o Oracle esta rodando...
netstat -an | findstr ":1521" >nul
if %errorlevel% neq 0 (
    echo ERRO: Oracle nao esta rodando na porta 1521
    echo Execute o Oracle primeiro e tente novamente
    pause
    exit /b 1
)

echo Oracle esta rodando. Continuando...
echo.

:: Conectar ao banco e executar o script
echo Conectando ao banco e criando usuario...
echo.

sqlplus consumo_esperto/consumo_esperto123@localhost:1521/XE @src\main\resources\db\create-test-user.sql

echo.
echo ========================================
echo    USUARIO DE TESTE CRIADO!
echo ========================================
echo.
echo Credenciais de login:
echo - Username: teste
echo - Email: teste@teste.com
echo - Senha: 123456
echo.
echo Agora voce pode fazer login no sistema!
echo.
pause
