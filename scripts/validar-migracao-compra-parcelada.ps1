# Executa teste de migracao CompraParcelada contra PostgreSQL real
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

function Load-DotEnv([string]$path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
            $map[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $map
}

& "$root\scripts\stop-integracao.ps1" -Force -ErrorAction SilentlyContinue
$dot = Load-DotEnv (Join-Path $root ".env")
$user = if ($dot['DATABASE_USERNAME']) { $dot['DATABASE_USERNAME'] } else { $dot['POSTGRES_USER'] }
$pass = if ($dot['DATABASE_PASSWORD']) { $dot['DATABASE_PASSWORD'] } else { $dot['POSTGRES_PASSWORD'] }
$banco = if ($env:INTEGRACAO_DB) { $env:INTEGRACAO_DB } else { "consumoesperto_integracao" }

$env:INTEGRACAO_POSTGRES_TEST = "1"
$env:INTEGRACAO_DATABASE_URL = "jdbc:postgresql://localhost:5432/$banco"
$env:INTEGRACAO_DATABASE_USERNAME = $user
$env:INTEGRACAO_DATABASE_PASSWORD = $pass

Write-Host "Migracao CompraParcelada PostgreSQL -> $banco" -ForegroundColor Cyan
& "$root\scripts\mvn-backend.ps1" test -Dtest=CompraParceladaMigracaoPostgresIntegrationTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Migracao PostgreSQL: OK" -ForegroundColor Green
