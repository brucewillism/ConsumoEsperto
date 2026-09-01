# Sobe frontend na porta 14200 para integracao local (dist estatico — evita falha de budget do ng serve)
param([switch]$Rebuild)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$pidFile = Join-Path $root "logs\integracao-frontend.pid"
$logFile = Join-Path $root "logs\integracao-frontend.log"
$port = if ($env:INTEGRACAO_FRONTEND_PORT) { [int]$env:INTEGRACAO_FRONTEND_PORT } else { 14200 }

if (Test-Path $pidFile) {
    $oldPid = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "Frontend integracao ja em execucao (PID $oldPid)." -ForegroundColor Yellow
        exit 0
    }
}

New-Item -ItemType Directory -Force -Path (Join-Path $root "logs") | Out-Null
$frontend = Join-Path $root "frontend"
$dist = Join-Path $frontend "dist\frontend\browser"

$npmCmd = Get-Command npm.cmd -ErrorAction SilentlyContinue
$npm = if ($npmCmd) { $npmCmd.Source } else { "npm.cmd" }

if ($Rebuild -or -not (Test-Path (Join-Path $dist "index.html"))) {
    Write-Host "Build frontend (ng build) ..." -ForegroundColor Cyan
    if (Test-Path $logFile) {
        try { Remove-Item $logFile -Force -ErrorAction Stop } catch {
            $logFile = Join-Path $root ("logs\integracao-frontend-{0}.log" -f (Get-Date -Format "yyyyMMddHHmmss"))
        }
    }
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $npm run build:integracao --prefix $frontend *> $logFile
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($buildExit -ne 0) { Write-Error "Build frontend falhou (exit $buildExit)" }
    & node (Join-Path $frontend "scripts\validate-integracao-bundle.mjs")
    if ($LASTEXITCODE -ne 0) { Write-Error "Validacao anti-producao do bundle falhou" }
}

if (-not (Test-Path (Join-Path $dist "index.html"))) {
    Write-Error "Dist ausente em $dist"
}

function Show-LogTail([string]$path, [int]$lines = 25) {
    if (-not (Test-Path $path)) { return }
    Write-Host "--- ultimas $lines linhas de $path ---" -ForegroundColor DarkYellow
    Get-Content $path -Tail $lines -ErrorAction SilentlyContinue | ForEach-Object { Write-Host $_ }
}

$npxCmd = Get-Command npx.cmd -ErrorAction SilentlyContinue
$npx = if ($npxCmd) { $npxCmd.Source } else { "npx.cmd" }
$serveLocal = Join-Path $frontend "node_modules\serve\bin\serve.js"
$serveExe = $null
$serveArgs = @()

if (Test-Path $serveLocal) {
    $nodeCmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $nodeCmd) { Write-Error "node.exe nao encontrado" }
    $serveExe = $nodeCmd.Source
    $serveArgs = @($serveLocal, "-s", "-l", "$port")
    Write-Host "Usando serve local: $serveLocal" -ForegroundColor Gray
} else {
    $serveExe = $npx
    $serveArgs = @("--yes", "serve", "-s", "-l", "$port")
    Write-Host "Usando npx serve (pacote remoto se necessario)" -ForegroundColor Gray
}

Write-Host "Servindo dist em http://localhost:$port (SPA fallback -s) ..." -ForegroundColor Green
$errLog = Join-Path $root "logs\integracao-frontend.err.log"
if (Test-Path $errLog) { Remove-Item $errLog -Force -ErrorAction SilentlyContinue }
if (Test-Path $logFile) { Remove-Item $logFile -Force -ErrorAction SilentlyContinue }

$proc = Start-Process -FilePath $serveExe `
    -ArgumentList $serveArgs `
    -WorkingDirectory $dist `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errLog `
    -PassThru -WindowStyle Hidden

$proc.Id | Set-Content $pidFile
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) {
        Show-LogTail $logFile
        Show-LogTail $errLog
        Write-Error "Frontend encerrou (exit $($proc.ExitCode)). Ver logs em logs\"
    }
    try {
        Invoke-WebRequest -Uri "http://localhost:$port" -TimeoutSec 3 -UseBasicParsing | Out-Null
        Write-Host "Frontend UP em http://localhost:$port (PID $($proc.Id))" -ForegroundColor Green
        exit 0
    } catch { Start-Sleep -Seconds 2 }
}
Show-LogTail $logFile
Show-LogTail $errLog
Write-Error "Timeout aguardando frontend em http://localhost:$port"
