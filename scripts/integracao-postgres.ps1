# Integracao PostgreSQL + backend (nao exibe senhas)
param(
    [string]$Banco = "consumoesperto_integracao",
    [switch]$CriarBanco,
    [switch]$RecriarBanco,
    [switch]$SubirBackend,
    [int]$Porta = 18081
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root ".env"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
if (-not (Test-Path $psql)) { $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe" }

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

$dot = Load-DotEnv $envFile
$user = if ($dot['DATABASE_USERNAME']) { $dot['DATABASE_USERNAME'] } else { $dot['POSTGRES_USER'] }
$pass = if ($dot['DATABASE_PASSWORD']) { $dot['DATABASE_PASSWORD'] } else { $dot['POSTGRES_PASSWORD'] }
$jwt  = $dot['JWT_SECRET']
if (-not $user -or -not $pass -or -not $jwt) {
    Write-Error "Defina POSTGRES_USER/POSTGRES_PASSWORD (ou DATABASE_*) e JWT_SECRET no .env"
}

$env:PGPASSWORD = $pass
Write-Host "PostgreSQL CLI: $psql" -ForegroundColor Cyan
& $psql --version

if ($RecriarBanco) {
    Write-Host "Recriando banco $Banco (drop + create) ..." -ForegroundColor Green
    & $psql -U $user -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$Banco' AND pid <> pg_backend_pid();" | Out-Null
    & $psql -U $user -d postgres -c "DROP DATABASE IF EXISTS $Banco"
    & $psql -U $user -d postgres -c "CREATE DATABASE $Banco"
    $CriarBanco = $true
}

if ($CriarBanco) {
    $exists = & $psql -U $user -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$Banco'" 2>&1
    if ($exists -ne "1") {
        Write-Host "Criando banco $Banco ..." -ForegroundColor Green
        & $psql -U $user -d postgres -c "CREATE DATABASE $Banco"
    } else {
        Write-Host "Banco $Banco ja existe." -ForegroundColor Yellow
    }
    Write-Host "Extensao vector (opcional)..." -ForegroundColor Gray
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $psql -U $user -d $Banco -c "CREATE EXTENSION IF NOT EXISTS vector" 2>&1 | Out-Null
    $ErrorActionPreference = $prevEap
    if ($LASTEXITCODE -ne 0) {
        Write-Host "pgvector indisponivel - backend usa BYTEA como fallback." -ForegroundColor Yellow
    }
}

$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/$Banco"
$env:DATABASE_USERNAME = $user
$env:DATABASE_PASSWORD = $pass
$env:JWT_SECRET = $jwt
$env:SERVER_PORT = "$Porta"

Write-Host "DATABASE_URL=$($env:DATABASE_URL)" -ForegroundColor Gray

if ($SubirBackend) {
    Push-Location (Join-Path $root "backend")
    Write-Host "Iniciando backend na porta $Porta (profile integracao, Flyway ON) ..." -ForegroundColor Green
    $env:SPRING_PROFILES_ACTIVE = "integracao"
    Remove-Item Env:SPRING_FLYWAY_ENABLED -ErrorAction SilentlyContinue
    & (Join-Path $root "scripts\mvn-backend.ps1") -q spring-boot:run "-Dspring-boot.run.jvmArguments=-Dserver.port=$Porta -Dspring.profiles.active=integracao"
    Pop-Location
}
