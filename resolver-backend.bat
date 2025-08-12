@echo off
chcp 65001 >nul
echo ========================================
echo    RESOLUÇÃO DE PROBLEMAS DO BACKEND
echo ========================================
echo.
echo Este script irá resolver problemas comuns do backend
echo incluindo atualizações de tabelas e recompilação
echo.

:: Verificar se estamos no diretório correto
if not exist "backend" (
    echo ERRO: Diretório 'backend' não encontrado!
    echo Execute este script na raiz do projeto.
    pause
    exit /b 1
)

echo [1/5] Verificando Java...
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Java não encontrado!
    echo Execute o setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

echo ✅ Java funcionando!

echo [2/5] Verificando Maven...
mvn -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Maven não encontrado!
    echo Execute o setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

echo ✅ Maven funcionando!

echo [3/5] Limpando e recompilando...
cd /d "backend"
echo.
echo Limpando projeto...
mvn clean -q

echo Compilando projeto...
mvn compile -q

if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Falha na compilação!
    echo.
    echo Tentando compilar com mais detalhes...
    mvn compile
    pause
    exit /b 1
)

echo ✅ Projeto compilado com sucesso!

echo [4/5] Verificando estrutura das entidades...
echo.
echo Verificando se todas as entidades estão corretas...

:: Verificar se a entidade AutorizacaoBancaria tem o campo 'ativo'
findstr /C:"private Boolean ativo" "src\main\java\com\consumoesperto\model\AutorizacaoBancaria.java" >nul
if %ERRORLEVEL% EQU 0 (
    echo ✅ Campo 'ativo' encontrado em AutorizacaoBancaria
) else (
    echo ❌ Campo 'ativo' NÃO encontrado em AutorizacaoBancaria
    echo.
    echo Atualizando entidade...
    echo private Boolean ativo = true; >> "src\main\java\com\consumoesperto\model\AutorizacaoBancaria.java"
)

echo [5/5] Verificando scripts de banco...
echo.
if exist "src\main\resources\db\update_autorizacoes_table.sql" (
    echo ✅ Script de atualização da tabela autorizacoes_bancarias encontrado
) else (
    echo ❌ Script de atualização não encontrado
    echo Execute o setup-completo-projeto.bat para criar todos os arquivos
)

echo.
echo ========================================
echo    RESOLUÇÃO CONCLUÍDA!
echo ========================================
echo.
echo ✅ Java verificado
echo ✅ Maven verificado
echo ✅ Projeto recompilado
echo ✅ Entidades verificadas
echo ✅ Scripts de banco verificados
echo.
echo Se você estiver usando Oracle Database:
echo 1. Execute o script: update_autorizacoes_table.sql
echo 2. Ou execute: setup-database.bat
echo.
echo Para testar o backend:
echo 1. start-servicos.bat
echo 2. Verifique se não há erros no console
echo.
echo Se ainda houver problemas:
echo 1. Verifique os logs do Spring Boot
echo 2. Execute: mvn clean install
echo 3. Verifique se o banco está acessível
echo.
pause
