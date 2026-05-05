# Maven no backend com JDK e Maven da pasta tools\ (igual ao rodar-backend.bat).
# Uso: .\scripts\mvn-backend.ps1 -q -DskipTests package
#      .\scripts\mvn-backend.ps1 spring-boot:run
# Para spring-boot:run com porta + perfil dev-evolution, use antes .\scripts\run-backend-dev-evolution.ps1
# (evita o Maven partir o -Dspring-boot.run.arguments quando se aninha powershell -File).
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jdkRoot = Join-Path $root "tools\java"
$jdks = @(Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue)
if ($jdks.Count -eq 0) {
    throw "Nenhum JDK em $jdkRoot. Instala o JDK 17 conforme tools\README.md"
}
$preferred = Join-Path $jdkRoot "ms-17.0.15"
if (Test-Path (Join-Path $preferred "bin\java.exe")) {
    $env:JAVA_HOME = $preferred
} else {
    $pick17 = $jdks | Where-Object { $_.Name -match '17' } | Sort-Object Name | Select-Object -First 1
    $env:JAVA_HOME = if ($pick17) { $pick17.FullName } else { $jdks[0].FullName }
}
$mvnBin = Join-Path $root "tools\maven\bin"
if (Test-Path $mvnBin) {
    $env:PATH = "$($env:JAVA_HOME)\bin;$mvnBin;$env:PATH"
} else {
    $env:PATH = "$($env:JAVA_HOME)\bin;$env:PATH"
    Write-Warning "tools\maven nao encontrado; a usar mvn do PATH."
}
Set-Location (Join-Path $root "backend")
& mvn @args
