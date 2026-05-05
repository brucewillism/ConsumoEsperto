@echo off
REM Para tudo nas portas do projeto + Evolution Node, depois sobe Evolution + backend 8081 + frontend 4200.
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
echo Parando servicos...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\parar-servicos.ps1"
echo.
echo Subindo de novo...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\subir-stack.ps1"
pause
