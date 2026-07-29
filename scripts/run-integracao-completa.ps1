# Pipeline completo: verify -> banco limpo -> JAR -> smoke -> stop
param(
    [string]$BaseUrl = "http://localhost:18081",
    [switch]$SkipVerify,
    [switch]$SkipSmoke
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

Write-Host "=== ConsumoEsperto integracao completa ===" -ForegroundColor Cyan

& (Join-Path $PSScriptRoot "stop-integracao.ps1") -Force -ErrorAction SilentlyContinue

if (-not $SkipVerify) {
    Write-Host "[1/5] mvn clean verify ..." -ForegroundColor Green
    Push-Location (Join-Path $root "backend")
    & (Join-Path $root "scripts\mvn-backend.ps1") clean verify
    if ($LASTEXITCODE -ne 0) { throw "mvn verify falhou" }
    Pop-Location
} else {
    Write-Host "[1/5] verify ignorado" -ForegroundColor Yellow
    Push-Location (Join-Path $root "backend")
    & (Join-Path $root "scripts\mvn-backend.ps1") -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package falhou" }
    Pop-Location
}

Write-Host "[2/5] Recriar banco integracao ..." -ForegroundColor Green
& (Join-Path $PSScriptRoot "integracao-postgres.ps1") -RecriarBanco

Write-Host "[3/5] Iniciar backend (JAR) ..." -ForegroundColor Green
& (Join-Path $PSScriptRoot "start-integracao.ps1")
if ($LASTEXITCODE -ne 0) { throw "start-integracao falhou" }

if (-not $SkipSmoke) {
    Write-Host "[4/7] Smoke HTTP ..." -ForegroundColor Green
    & (Join-Path $PSScriptRoot "smoke-integracao.ps1") -BaseUrl $BaseUrl -BancoLimpo
    if ($LASTEXITCODE -ne 0) { throw "smoke falhou" }

    Write-Host "[5/7] CSV runtime ..." -ForegroundColor Green
    & (Join-Path $PSScriptRoot "validar-csv-integracao.ps1") -BaseUrl $BaseUrl
    if ($LASTEXITCODE -ne 0) { throw "validacao CSV falhou" }

    Write-Host "[6/7] PDF/Motor runtime ..." -ForegroundColor Green
    & (Join-Path $PSScriptRoot "validar-pdf-motor-integracao.ps1") -BaseUrl $BaseUrl
    if ($LASTEXITCODE -ne 0) { throw "validacao PDF/Motor falhou" }
} else {
    Write-Host "[4-6/7] smoke/CSV/PDF ignorados" -ForegroundColor Yellow
}

Write-Host "[7/7] Encerrar backend ..." -ForegroundColor Green
& (Join-Path $PSScriptRoot "stop-integracao.ps1") -Force

Write-Host "=== Integracao completa OK ===" -ForegroundColor Green
