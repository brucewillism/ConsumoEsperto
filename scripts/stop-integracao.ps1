# Encerra backend de integracao iniciado por start-integracao.ps1
param([int]$Porta = 18081, [switch]$Force)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$pidFile = Join-Path $root "logs\integracao-backend.pid"

function Stop-SafeProcess([int]$processId, [string]$reason) {
    $p = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $p) { return $false }
    Write-Host "Encerrando PID $processId ($reason) ..." -ForegroundColor Yellow
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    return $true
}

$stopped = $false
if (Test-Path $pidFile) {
    $backendPid = [int](Get-Content $pidFile -ErrorAction SilentlyContinue)
    if ($backendPid -gt 0) {
        $stopped = Stop-SafeProcess $backendPid "pid file"
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$conn = Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
    $ownerPid = $conn.OwningProcess
    if (-not $stopped -or $Force) {
        if ($Force -or (Test-Path $pidFile)) {
            Stop-SafeProcess $ownerPid "porta $Porta" | Out-Null
        } else {
            Write-Host "Porta $Porta ocupada por PID $ownerPid (nao gerenciado por integracao). Use -Force para encerrar." -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host "Backend integracao parado." -ForegroundColor Green
