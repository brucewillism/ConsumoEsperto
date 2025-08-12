@echo off
chcp 65001 >nul
echo ========================================
echo    PARANDO SERVIÇOS - CONSUMO ESPERTO
echo ========================================
echo.

echo Parando todos os serviços...

:: Parar processos do backend (porta 8080)
echo Parando backend (porta 8080)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080') do (
    taskkill /f /pid %%a >nul 2>&1
    echo Backend parado.
)

:: Parar processos do frontend (porta 4200)
echo Parando frontend (porta 4200)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :4200') do (
    taskkill /f /pid %%a >nul 2>&1
    echo Frontend parado.
)

:: Parar processos do ngrok (porta 4040)
echo Parando ngrok (porta 4040)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :4040') do (
    taskkill /f /pid %%a >nul 2>&1
    echo ngrok parado.
)

:: Parar janelas específicas
echo Fechando janelas da aplicação...
taskkill /f /im "Backend - Consumo Esperto" >nul 2>&1
taskkill /f /im "Frontend - Consumo Esperto" >nul 2>&1
taskkill /f /im "ngrok" >nul 2>&1

echo.
echo ========================================
echo    TODOS OS SERVIÇOS PARADOS!
echo ========================================
echo.
echo Para iniciar novamente, execute:
echo setup-completo-projeto.bat
echo.
pause
