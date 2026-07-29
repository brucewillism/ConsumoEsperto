# Para o backend de integracao (JAR, nao spring-boot:run durante verify)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$pidFile = Join-Path $root "logs\integracao-backend.pid"
$logFile = Join-Path $root "logs\integracao-backend.log"
$port = if ($env:INTEGRACAO_PORT) { [int]$env:INTEGRACAO_PORT } else { 18081 }

function Test-JavaBin([string]$javaHome) {
    if (-not $javaHome) { return $false }
    return (Test-Path (Join-Path $javaHome "bin/java")) -or (Test-Path (Join-Path $javaHome "bin/java.exe"))
}

function Resolve-JavaHomeFromPath {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { return $null }
    $bin = Split-Path $java.Source -Parent
    return (Resolve-Path (Join-Path $bin "..")).Path
}

function Resolve-IntegracaoJavaExe {
    $jdkRoot = Join-Path $root "tools/java"
    $jdks = @(Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue)
    $javaHome = $null
    if ($jdks.Count -eq 0) {
        if (Test-JavaBin $env:JAVA_HOME) {
            $javaHome = $env:JAVA_HOME
            Write-Host "Usando JAVA_HOME do ambiente: $javaHome"
        } else {
            $fromPath = Resolve-JavaHomeFromPath
            if ($fromPath -and (Test-JavaBin $fromPath)) {
                $javaHome = $fromPath
                Write-Host "Usando JDK do PATH: $javaHome"
            } else {
                $jdksUser = Join-Path $env:USERPROFILE ".jdks"
                if (Test-Path $jdksUser) {
                    $pick17 = Get-ChildItem $jdksUser -Directory -ErrorAction SilentlyContinue |
                        Where-Object { $_.Name -match '17' } | Sort-Object Name | Select-Object -First 1
                    if ($pick17 -and (Test-JavaBin $pick17.FullName)) {
                        $javaHome = $pick17.FullName
                        Write-Host "Usando JDK em .jdks: $javaHome"
                    }
                }
            }
        }
        if (-not $javaHome) {
            throw "Nenhum JDK 17 encontrado (tools\java, JAVA_HOME, PATH ou .jdks). Instale conforme tools\README.md."
        }
    } else {
        $preferred = Join-Path $jdkRoot "ms-17.0.15"
        if (Test-JavaBin $preferred) {
            $javaHome = $preferred
        } else {
            $pick17 = $jdks | Where-Object { $_.Name -match '17' } | Sort-Object Name | Select-Object -First 1
            $javaHome = if ($pick17) { $pick17.FullName } else { $jdks[0].FullName }
        }
        Write-Host "Usando JDK em tools\java: $javaHome"
    }
    $exe = Join-Path $javaHome "bin\java.exe"
    if (-not (Test-Path $exe)) {
        $exe = Join-Path $javaHome "bin\java"
    }
    if (-not (Test-Path $exe)) {
        throw "java.exe nao encontrado em $javaHome\bin"
    }
    return (Resolve-Path $exe).Path
}

$javaExe = Resolve-IntegracaoJavaExe

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

if (Test-Path $pidFile) {
    $oldPid = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "Backend integracao ja em execucao (PID $oldPid)." -ForegroundColor Yellow
        exit 0
    }
}

$dot = Load-DotEnv (Join-Path $root ".env")
$user = if ($dot['DATABASE_USERNAME']) { $dot['DATABASE_USERNAME'] } else { $dot['POSTGRES_USER'] }
$pass = if ($dot['DATABASE_PASSWORD']) { $dot['DATABASE_PASSWORD'] } else { $dot['POSTGRES_PASSWORD'] }
$jwt  = $dot['JWT_SECRET']
$banco = if ($env:INTEGRACAO_DB) { $env:INTEGRACAO_DB } else { "consumoesperto_integracao" }
if (-not $user -or -not $pass -or -not $jwt) {
    Write-Error "Defina credenciais e JWT_SECRET no .env"
}

$jars = Get-ChildItem (Join-Path $root "backend\target\*.jar") -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|javadoc|original' } |
    Sort-Object LastWriteTime -Descending
if (-not $jars -or $jars.Count -eq 0) {
    Write-Error "JAR nao encontrado. Execute: .\scripts\mvn-backend.ps1 clean package -DskipTests"
}
$jar = $jars[0].FullName

New-Item -ItemType Directory -Force -Path (Join-Path $root "logs") | Out-Null

$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/$banco"
$env:DATABASE_USERNAME = $user
$env:DATABASE_PASSWORD = $pass
$env:JWT_SECRET = $jwt
$env:SERVER_PORT = "$port"
$env:SPRING_PROFILES_ACTIVE = "integracao"

Write-Host "Iniciando JAR integracao na porta $port ..." -ForegroundColor Green
Write-Host "Java: $javaExe" -ForegroundColor Gray
Write-Host "Log: $logFile" -ForegroundColor Gray

$logErr = Join-Path $root "logs\integracao-backend.err.log"

$proc = Start-Process -FilePath $javaExe `
    -ArgumentList @("-jar", $jar, "--spring.profiles.active=integracao", "--server.port=$port") `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $logErr `
    -PassThru -WindowStyle Hidden

$proc.Id | Set-Content $pidFile
Write-Host "PID $($proc.Id) gravado em $pidFile"

$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) {
        Write-Error "Backend encerrou prematuramente (exit $($proc.ExitCode)). Ver $logFile"
    }
    try {
        $h = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 3
        if ($h.status -eq "UP") {
            Write-Host "Health UP" -ForegroundColor Green
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 2
}
Write-Error "Timeout aguardando health UP. Ver $logFile"
