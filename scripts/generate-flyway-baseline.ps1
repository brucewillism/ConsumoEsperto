# Script: gera baseline Flyway a partir de schema Hibernate + SchemaAutoPatch em banco temporario.
# Nao imprime credenciais. Requer PostgreSQL local e .env na raiz do repo.
param(
    [string]$BancoTemp = "consumoesperto_baseline_gen",
    [int]$Porta = 18099
)
$ErrorActionPreference = "Stop"
function Invoke-Psql([string[]]$Args) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $psql @Args 2>&1 | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_ } }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    if ($code -ne 0) { throw "psql exit $code" }
}
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root ".env"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$pgDump = "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe"
if (-not (Test-Path $psql)) { $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"; $pgDump = "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe" }

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
    Write-Error "Defina POSTGRES_USER/POSTGRES_PASSWORD e JWT_SECRET no .env"
}

$env:PGPASSWORD = $pass
Write-Host "=== Geracao baseline Flyway ===" -ForegroundColor Cyan

Invoke-Psql @("-U", $user, "-d", "postgres", "-c", "DROP DATABASE IF EXISTS $BancoTemp WITH (FORCE)") | Out-Null
Invoke-Psql @("-U", $user, "-d", "postgres", "-c", "CREATE DATABASE $BancoTemp") | Out-Null
Write-Host "Banco temporario $BancoTemp criado." -ForegroundColor Green

$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/$BancoTemp"
$env:DATABASE_USERNAME = $user
$env:DATABASE_PASSWORD = $pass
$env:JWT_SECRET = $jwt
$env:SPRING_PROFILES_ACTIVE = "baseline-gen"

$backend = Join-Path $root "backend"
Push-Location $backend
$proc = Start-Process -FilePath "mvn" -ArgumentList @("-q", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-Dserver.port=$Porta") -PassThru -NoNewWindow
Pop-Location

$deadline = (Get-Date).AddMinutes(4)
$started = $false
while ((Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod "http://localhost:$Porta/actuator/health" -TimeoutSec 3
        if ($health.status -eq "UP") { $started = $true; break }
    } catch { Start-Sleep -Seconds 3 }
}
if (-not $started) {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Write-Error "Backend nao iniciou para geracao de baseline."
}

Write-Host "Schema materializado. Exportando pg_dump..." -ForegroundColor Green
$tableCount = (Invoke-Psql @("-U", $user, "-d", $BancoTemp, "-tAc", "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'") | Out-String).Trim()
Write-Host "Tabelas public: $tableCount" -ForegroundColor Gray
if ([int]$tableCount -lt 10) {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Write-Error "Schema incompleto ($tableCount tabelas). Verifique ddl-auto=create no profile baseline-gen."
}
$outDir = Join-Path $root "backend\src\main\resources\db\migration"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir "V1__baseline_inicial.sql"
& $pgDump -U $user -d $BancoTemp --schema-only --no-owner --no-privileges -f $outFile
if ($LASTEXITCODE -ne 0) { Write-Error "pg_dump falhou." }

if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
Invoke-Psql @("-U", $user, "-d", "postgres", "-c", "DROP DATABASE IF EXISTS $BancoTemp WITH (FORCE)") | Out-Null

$lines = (Get-Content $outFile | Measure-Object -Line).Lines
Write-Host "Baseline gerada: $outFile ($lines linhas)" -ForegroundColor Green
