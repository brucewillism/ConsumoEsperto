$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
& "$root\scripts\stop-frontend-integracao.ps1" -Force -ErrorAction SilentlyContinue
& "$root\scripts\stop-integracao.ps1" -Force -ErrorAction SilentlyContinue
foreach ($port in 14200, 18081) {
    Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}
Remove-Item @(
    "$root\logs\integracao-frontend.log",
    "$root\logs\integracao-frontend.err.log",
    "$root\logs\integracao-frontend.pid",
    "$root\logs\integracao-backend.pid"
) -Force -ErrorAction SilentlyContinue
Write-Host "CLEANED"
