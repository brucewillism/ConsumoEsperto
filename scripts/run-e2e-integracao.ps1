# Pipeline E2E: backend + frontend + Playwright
param(
    [switch]$SkipBanco,
    [switch]$ManterServicos
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$env:E2E_BASE_URL = "http://localhost:14200"
$env:E2E_API_URL = "http://localhost:18081"
$env:CI = "1"

try {
    & "$root\scripts\stop-integracao.ps1" -Force -ErrorAction SilentlyContinue
    & "$root\scripts\stop-frontend-integracao.ps1" -Force -ErrorAction SilentlyContinue

    if (-not $SkipBanco) {
        & "$root\scripts\integracao-postgres.ps1" -RecriarBanco
    }

    & "$root\scripts\mvn-backend.ps1" clean package -DskipTests | Out-Null
    & "$root\scripts\start-integracao.ps1"
    & "$root\scripts\start-frontend-integracao.ps1"

    Set-Location (Join-Path $root "e2e")
    npm ci 2>&1 | Out-Null
    npx playwright install chromium 2>&1 | Out-Null
    npx playwright test --list
    npm test
    $code = $LASTEXITCODE
    if ($code -ne 0) { exit $code }
} finally {
    if (-not $ManterServicos) {
        Set-Location $root
        & "$root\scripts\stop-frontend-integracao.ps1" -Force -ErrorAction SilentlyContinue
        & "$root\scripts\stop-integracao.ps1" -Force -ErrorAction SilentlyContinue
    }
}
