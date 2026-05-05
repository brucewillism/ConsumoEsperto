@echo off
REM Evolution Node (tools\evolution-api ou EVOLUTION_DIR) + backend 8081 + frontend 4200
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\subir-stack.ps1"
pause
