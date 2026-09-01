# Roda apenas os cenários E2E que falharam na última regressão
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$env:E2E_BASE_URL = "http://localhost:14200"
$env:E2E_API_URL = "http://localhost:18081"
$env:CI = "1"

try {
    & "$root\scripts\cleanup-integracao-ports.ps1"
    & "$root\scripts\integracao-postgres.ps1" -RecriarBanco
    & "$root\scripts\start-integracao.ps1"
    & "$root\scripts\start-frontend-integracao.ps1" -Rebuild

    Set-Location (Join-Path $root "e2e")
    cmd /c "npx playwright install chromium >nul 2>&1"
    & npx.cmd playwright test tests/fluxos-criticos.spec.ts --reporter=line
    exit $LASTEXITCODE
} finally {
    Set-Location $root
    & "$root\scripts\cleanup-integracao-ports.ps1"
}
