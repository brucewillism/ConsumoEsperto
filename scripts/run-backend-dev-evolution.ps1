# Spring Boot na 18081 com perfil dev-evolution (JDK/Maven em tools\).
# Uso: .\scripts\run-backend-dev-evolution.ps1
# Portas da stack: scripts/stack-ports.ps1
#
# Se aparecer "Port 18081 was already in use": pare a outra instancia (outro terminal/CMD) ou:
#   Get-NetTCPConnection -LocalPort 18081 | Select-Object LocalPort,OwningProcess,State
#   Stop-Process -Id <PID> -Force
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jdkRoot = Join-Path $root "tools\java"
$preferred = Join-Path $jdkRoot "ms-17.0.15"
if (Test-Path (Join-Path $preferred "bin\java.exe")) {
    $jdk = $preferred
} else {
    $jdks = @(Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue)
    if ($jdks.Count -eq 0) { throw "Nenhum JDK em tools\java" }
    $pick17 = $jdks | Where-Object { $_.Name -match '17' } | Sort-Object Name | Select-Object -First 1
    $jdk = if ($pick17) { $pick17.FullName } else { $jdks[0].FullName }
}
$env:JAVA_HOME = $jdk
$inUse = Get-NetTCPConnection -LocalPort 18081 -State Listen -ErrorAction SilentlyContinue
if ($inUse) {
    $p = $inUse[0].OwningProcess
    Write-Error "Porta 18081 ocupada (PID $p). Executa parar-servicos.bat na raiz ou: Stop-Process -Id $p -Force"
    exit 1
}
$mvnBin = Join-Path $root "tools\maven\bin"
$env:PATH = "$($env:JAVA_HOME)\bin;$mvnBin;$env:PATH"
Set-Location (Join-Path $root "backend")
$mvnArgs = @(
    "spring-boot:run",
    "-Dspring-boot.run.arguments=--server.port=18081 --spring.profiles.active=dev-evolution"
)
& mvn @mvnArgs
