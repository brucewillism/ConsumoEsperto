@echo off
echo Parando servicos do ConsumoEsperto (portas 4200 / 8080 / 8081, apenas processos deste repo)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\parar-servicos.ps1"
pause
