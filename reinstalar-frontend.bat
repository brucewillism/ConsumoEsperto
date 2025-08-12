@echo off
chcp 65001 >nul
echo ========================================
echo    REINSTALAÇÃO DO FRONTEND
echo ========================================
echo.
echo Este script irá reinstalar todas as dependências do frontend
echo para resolver problemas com Angular Material
echo.

:: Verificar se estamos no diretório correto
if not exist "frontend" (
    echo ERRO: Diretório 'frontend' não encontrado!
    echo Execute este script na raiz do projeto.
    pause
    exit /b 1
)

echo [1/4] Limpando dependências antigas...
cd /d "frontend"

:: Remover node_modules e package-lock.json
if exist "node_modules" (
    echo Removendo node_modules...
    rmdir /s /q "node_modules"
)

if exist "package-lock.json" (
    echo Removendo package-lock.json...
    del "package-lock.json"
)

if exist ".angular" (
    echo Removendo cache do Angular...
    rmdir /s /q ".angular"
)

echo ✅ Dependências antigas removidas!

echo [2/4] Verificando Node.js...
node --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Node.js não encontrado!
    echo Execute o setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

npm --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERRO: npm não encontrado!
    echo Execute o setup-completo-projeto.bat primeiro.
    pause
    exit /b 1
)

echo ✅ Node.js e npm funcionando!

echo [3/4] Instalando dependências...
echo.
echo Instalando dependências do Angular Material...
npm install --silent

if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Falha ao instalar dependências!
    echo.
    echo Tentando instalar com mais detalhes...
    npm install
    pause
    exit /b 1
)

echo ✅ Dependências instaladas com sucesso!

echo [4/4] Verificando instalação...
echo.
echo Verificando se Angular Material foi instalado...
if exist "node_modules\@angular\material" (
    echo ✅ Angular Material instalado corretamente!
) else (
    echo ❌ Angular Material não foi instalado!
    echo.
    echo Tentando instalar especificamente...
    npm install @angular/material @angular/cdk @angular/animations --save
)

echo.
echo ========================================
echo    REINSTALAÇÃO CONCLUÍDA!
echo ========================================
echo.
echo ✅ Dependências reinstaladas
echo ✅ Cache limpo
echo ✅ Angular Material verificado
echo.
echo Agora tente executar o frontend:
echo cd frontend
echo npm start
echo.
echo Se ainda houver problemas, verifique:
echo 1. Se o Node.js está na versão correta (18+)
echo 2. Se há conflitos de versão
echo 3. Se o proxy da empresa está bloqueando
echo.
pause
