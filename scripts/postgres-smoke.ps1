# Smoke test PostgreSQL local - nao destrutivo em banco de producao
param(
    [string]$BancoTeste = "consumoesperto_test_smoke",
    [switch]$CriarBanco,
    [switch]$RemoverBanco
)
$ErrorActionPreference = "Stop"

Write-Host "=== Verificacao PostgreSQL local ===" -ForegroundColor Cyan
Get-Service *postgres* -ErrorAction SilentlyContinue | Format-Table Name, Status -AutoSize
$psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
$psql = if ($psqlCmd) { $psqlCmd.Source } else { $null }
if (-not $psql) {
    $candidates = @(
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\18\bin\psql.exe"
    )
    $psql = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $psql) {
    Write-Host "PostgreSQL CLI nao encontrado." -ForegroundColor Yellow
    exit 2
}
& $psql --version

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
$dbUrl = $env:DATABASE_URL
if (-not $dbUrl -and (Test-Path $envFile)) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^DATABASE_URL=(.+)$') { $dbUrl = $matches[1].Trim() }
    }
}
if (-not $dbUrl) { $dbUrl = "postgresql://localhost:5432/postgres" }
Write-Host "Datasource (sem credenciais): $($dbUrl -replace '://[^@]+@','://***@')" -ForegroundColor Gray

if ($CriarBanco) {
    Write-Host "Criando banco de teste: $BancoTeste" -ForegroundColor Green
    & $psql -d postgres -c "CREATE DATABASE $BancoTeste" 2>$null
}

if ($RemoverBanco) {
    Write-Host "Removendo banco de teste: $BancoTeste" -ForegroundColor Yellow
    & $psql -d postgres -c "DROP DATABASE IF EXISTS $BancoTeste WITH (FORCE)"
}

Write-Host "Smoke concluido." -ForegroundColor Green
