@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LIMPEZA E REINSTALACAO
echo ========================================
echo.

echo Este script ira:
echo 1. Limpar ferramentas corrompidas
echo 2. Remover downloads corrompidos
echo 3. Reexecutar setup completo
echo.

echo ATENCAO: Todas as ferramentas serao removidas!
echo.
set /p confirm="Tem certeza? (S/N): "

if /i not "%confirm%"=="S" (
    echo Operacao cancelada.
    pause
    exit /b 0
)

echo.
echo ========================================
echo    LIMPANDO FERRAMENTAS...
echo ========================================

:: Remover pasta tools
if exist "tools" (
    echo Removendo pasta tools...
    rmdir /s /q "tools"
    echo ✅ Pasta tools removida
) else (
    echo ℹ️  Pasta tools nao encontrada
)

:: Remover downloads corrompidos
echo.
echo Removendo downloads corrompidos...

if exist "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip" (
    del "%TEMP%\openjdk-17.0.2_windows-x64_bin.zip"
    echo ✅ Java JDK removido
)

if exist "%TEMP%\apache-maven-3.9.6-bin.zip" (
    del "%TEMP%\apache-maven-3.9.6-bin.zip"
    echo ✅ Maven removido
)

if exist "%TEMP%\node-v18.19.0-win-x64.zip" (
    del "%TEMP%\node-v18.19.0-win-x64.zip"
    echo ✅ Node.js removido
)

if exist "%TEMP%\OracleXE213_Win64.zip" (
    del "%TEMP%\OracleXE213_Win64.zip"
    echo ✅ Oracle removido
)

if exist "%TEMP%\oracle" (
    rmdir /s /q "%TEMP%\oracle"
    echo ✅ Pasta Oracle temporaria removida
)

echo.
echo ========================================
echo    LIMPEZA CONCLUIDA!
echo ========================================
echo.
echo Agora execute o setup completo:
echo.
echo setup-completo-projeto.bat
echo.
echo Pressione qualquer tecla para sair...
pause >nul
