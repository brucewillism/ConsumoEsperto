param([switch]$Force)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$pidFile = Join-Path $root "logs\integracao-frontend.pid"
$port = if ($env:INTEGRACAO_FRONTEND_PORT) { [int]$env:INTEGRACAO_FRONTEND_PORT } else { 14200 }

function Stop-SafePid([int]$targetPid, [string]$label) {
    if (-not $targetPid) { return }
    $p = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
    if (-not $p) { return }
    $cmd = ($p | Select-Object -ExpandProperty Path -ErrorAction SilentlyContinue)
    if ($cmd -match 'node|npm|ng') {
        Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
        Write-Host "$label PID $targetPid encerrado." -ForegroundColor Gray
    } elseif ($Force) {
        Write-Warning "PID $targetPid nao parece node/npm - ignorado."
    }
}

if (Test-Path $pidFile) {
    Stop-SafePid ([int](Get-Content $pidFile)) "Frontend"
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-SafePid $_.OwningProcess "Porta $port"
}
Write-Host "Frontend integracao parado." -ForegroundColor Green
