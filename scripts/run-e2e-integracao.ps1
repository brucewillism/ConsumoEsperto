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
    & "$root\scripts\cleanup-integracao-ports.ps1"

    if (-not $SkipBanco) {
        & "$root\scripts\integracao-postgres.ps1" -RecriarBanco
    }

    & "$root\scripts\mvn-backend.ps1" clean package -DskipTests | Out-Null
    & "$root\scripts\start-integracao.ps1"
    # -Rebuild garante bundle com configuration=integracao (dist antigo pode ser de produção,
    # com apiUrl no domínio público — login E2E falharia silenciosamente)
    & "$root\scripts\start-frontend-integracao.ps1" -Rebuild

    Set-Location (Join-Path $root "e2e")
    # cmd /c evita que warnings do npm em stderr virem NativeCommandError com ErrorActionPreference=Stop
    cmd /c "npm ci >nul 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "npm ci falhou" }
    cmd /c "npx playwright install chromium >nul 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "playwright install falhou" }
    cmd /c "npx playwright test --list"
    cmd /c "npm test"
    $code = $LASTEXITCODE
    if ($code -ne 0) { exit $code }
} finally {
    if (-not $ManterServicos) {
        Set-Location $root
        & "$root\scripts\cleanup-integracao-ports.ps1"
    }
}
