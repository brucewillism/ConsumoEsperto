@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LIMPEZA DE ARQUIVOS ANTIGOS
echo ========================================
echo.
echo Este script remove arquivos que foram integrados no setup-completo-projeto.bat
echo.

echo Arquivos que serão removidos:
echo - configurar-jdk.bat (integrado no setup completo)
echo - SOLUCAO_JDK_README.md (criado automaticamente)
echo - CONFIGURACAO_BANCO_README.md (criado automaticamente)
echo - CONFIGURACAO_APIS_BANCARIAS_README.md (criado automaticamente)
echo.

echo Deseja continuar? (S/N)
set /p CONFIRMA=

if /i "%CONFIRMA%"=="S" (
    echo.
    echo Removendo arquivos antigos...
    
    if exist "configurar-jdk.bat" (
        del "configurar-jdk.bat"
        echo ✅ configurar-jdk.bat removido
    )
    
    if exist "SOLUCAO_JDK_README.md" (
        del "SOLUCAO_JDK_README.md"
        echo ✅ SOLUCAO_JDK_README.md removido
    )
    
    if exist "CONFIGURACAO_BANCO_README.md" (
        del "CONFIGURACAO_BANCO_README.md"
        echo ✅ CONFIGURACAO_BANCO_README.md removido
    )
    
    if exist "CONFIGURACAO_APIS_BANCARIAS_README.md" (
        del "CONFIGURACAO_APIS_BANCARIAS_README.md"
        echo ✅ CONFIGURACAO_APIS_BANCARIAS_README.md removido
    )
    
    echo.
    echo ========================================
    echo    LIMPEZA CONCLUÍDA!
    echo ========================================
    echo.
    echo ✅ Arquivos antigos removidos
    echo ✅ Tudo integrado no setup-completo-projeto.bat
    echo.
    echo Agora você tem apenas um script principal que faz tudo!
    echo Execute: setup-completo-projeto.bat
    echo.
) else (
    echo.
    echo Limpeza cancelada.
    echo Os arquivos antigos foram mantidos.
)

pause
