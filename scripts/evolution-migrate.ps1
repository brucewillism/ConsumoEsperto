# Aplica migracoes Prisma da Evolution sem prompt interativo do xcopy (Windows).
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$evo = Join-Path $root "tools\evolution-api"
if (-not (Test-Path (Join-Path $evo "package.json"))) {
    throw "Evolution nao encontrada em $evo"
}
& (Join-Path $root "scripts\sincronizar-evolution-env.ps1")
Push-Location $evo
try {
    if (Test-Path "prisma\migrations") {
        Remove-Item "prisma\migrations" -Recurse -Force
    }
    npm run db:deploy:win
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
