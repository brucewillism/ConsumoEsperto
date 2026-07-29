# Backup/restore PostgreSQL integracao (sem senha na linha de comando visivel)
param(
    [string]$BancoOrigem = "consumoesperto_integracao",
    [string]$BancoRestore = "consumoesperto_restore_test",
    [string]$BaseUrl = "http://localhost:18081"
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$pgDump = "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe"
$pgRestore = "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
if (-not (Test-Path $pgDump)) { $pgDump = "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe"; $pgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"; $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe" }

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

$dot = Load-DotEnv (Join-Path $root ".env")
$user = if ($dot['DATABASE_USERNAME']) { $dot['DATABASE_USERNAME'] } else { $dot['POSTGRES_USER'] }
$pass = if ($dot['DATABASE_PASSWORD']) { $dot['DATABASE_PASSWORD'] } else { $dot['POSTGRES_PASSWORD'] }
if (-not $user -or -not $pass) { Write-Error "Credenciais ausentes no .env" }

$dumpDir = Join-Path $root "logs\backup-test"
New-Item -ItemType Directory -Force -Path $dumpDir | Out-Null
$dumpFile = Join-Path $dumpDir "integracao-$(Get-Date -Format 'yyyyMMddHHmmss').dump"
$pgpassFile = Join-Path $dumpDir ".pgpass.tmp"
"localhost:5432:*:${user}:${pass}" | Set-Content $pgpassFile -Encoding ASCII -NoNewline
$env:PGPASSFILE = $pgpassFile

Write-Host "[1] pg_dump schema+dados ..." -ForegroundColor Green
& $pgDump -h localhost -U $user -d $BancoOrigem -Fc --no-owner --no-privileges -f $dumpFile
if ($LASTEXITCODE -ne 0) { Remove-Item $pgpassFile -Force -ErrorAction SilentlyContinue; throw "pg_dump falhou" }

Write-Host "[2] recriar banco restore ..." -ForegroundColor Green
& $psql -U $user -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$BancoRestore' AND pid <> pg_backend_pid();" | Out-Null
& $psql -U $user -d postgres -c "DROP DATABASE IF EXISTS $BancoRestore"
& $psql -U $user -d postgres -c "CREATE DATABASE $BancoRestore"

Write-Host "[3] pg_restore ..." -ForegroundColor Green
& $pgRestore -h localhost -U $user -d $BancoRestore --no-owner --no-privileges $dumpFile 2>&1 | Out-Null
# pg_restore pode retornar warnings; verificar tabelas

$cntOrig = & $psql -U $user -d $BancoOrigem -tAc "SELECT COUNT(*) FROM usuarios"
$cntRest = & $psql -U $user -d $BancoRestore -tAc "SELECT COUNT(*) FROM usuarios"
$flyOrig = & $psql -U $user -d $BancoOrigem -tAc "SELECT MAX(version) FROM flyway_schema_history"
$flyRest = & $psql -U $user -d $BancoRestore -tAc "SELECT MAX(version) FROM flyway_schema_history"

Remove-Item $pgpassFile -Force -ErrorAction SilentlyContinue
Remove-Item Env:PGPASSFILE -ErrorAction SilentlyContinue

$cmpOk = ($cntOrig -eq $cntRest) -and ($flyOrig -eq $flyRest)
Write-Host "Comparacao: usuarios orig=$cntOrig restore=$cntRest flyway=$flyOrig/$flyRest" -ForegroundColor $(if ($cmpOk) { "Green" } else { "Yellow" })

$report = Join-Path $dumpDir "backup-restore-report.json"
@{
    dump = $dumpFile; usuariosOrigem = $cntOrig; usuariosRestore = $cntRest
    flywayOrigem = $flyOrig; flywayRestore = $flyRest; comparacao = $(if ($cmpOk) { "OK" } else { "DIVERGENTE" })
} | ConvertTo-Json | Set-Content $report -Encoding UTF8
Write-Host "Relatorio: $report" -ForegroundColor Cyan

# Smoke contra banco restaurado
Write-Host "[4] Backend contra banco restore ..." -ForegroundColor Green
$env:INTEGRACAO_DB = $BancoRestore
& (Join-Path $root "scripts\start-integracao.ps1") 2>&1 | Out-Null
Start-Sleep -Seconds 3
$healthOk = $false
try {
    $h = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 10
    $healthOk = ($h.status -eq "UP")
} catch { }

$restoreSmoke = @{ health = $(if ($healthOk) { "OK" } else { "FALHA" }); backendRestaurado = $(if ($healthOk) { "OK" } else { "FALHA" }) }
if ($healthOk) {
    $sfx = Get-Date -Format "yyyyMMddHHmmss"
    $u = @{ username = "restore.$sfx@test.local"; email = "restore.$sfx@test.local"; password = "SenhaTeste123!"; nome = "Restore Test" }
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/auth/registro" -Method Post -Body ($u | ConvertTo-Json) -ContentType "application/json" | Out-Null
        $tok = (Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body (@{ username = $u.email; password = $u.password } | ConvertTo-Json) -ContentType "application/json").token
        $hAuth = @{ Authorization = "Bearer $tok" }
        Invoke-RestMethod -Uri "$BaseUrl/api/relatorios/mensal?ano=$((Get-Date).Year)&mes=$((Get-Date).Month)" -Headers $hAuth | Out-Null
        $restoreSmoke.login = "OK"; $restoreSmoke.relatorio = "OK"
    } catch { $restoreSmoke.login = "FALHA" }
}
& (Join-Path $root "scripts\stop-integracao.ps1") -Force -ErrorAction SilentlyContinue
Remove-Item Env:INTEGRACAO_DB -ErrorAction SilentlyContinue

$reportObj = Get-Content $report -Raw | ConvertFrom-Json
$reportObj | Add-Member -NotePropertyName restoreSmoke -NotePropertyValue $restoreSmoke -Force
$reportObj | ConvertTo-Json -Depth 5 | Set-Content $report -Encoding UTF8

if (-not $cmpOk) { exit 1 }
if (-not $healthOk) { exit 1 }
