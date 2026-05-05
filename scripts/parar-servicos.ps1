# Para Evolution Node, backend Spring, ng serve e listeners nas portas do projeto.
$ErrorActionPreference = "SilentlyContinue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$evoDir = if ($env:EVOLUTION_DIR) { $env:EVOLUTION_DIR } else { Join-Path $root "tools\evolution-api" }
$procsNodeEvolution = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq "node.exe" -and $_.CommandLine -and (
        $_.CommandLine -like "*$evoDir*" -or
        $_.CommandLine -like "*evolution-api*main.js*" -or
        $_.CommandLine -like "*evolution-api*dist*"
    )
}
foreach ($p in $procsNodeEvolution) {
    Write-Host "Encerrando Evolution Node PID $($p.ProcessId)..."
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

$projectPorts = @(4200, 8080, 8081, 4040)
foreach ($port in $projectPorts) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        $owningPid = $c.OwningProcess
        Write-Host "Encerrando PID $owningPid (porta $port)..."
        Stop-Process -Id $owningPid -Force -ErrorAction SilentlyContinue
    }
}

$procs = Get-CimInstance Win32_Process | Where-Object {
    $_.CommandLine -and $_.CommandLine -like "*$root*" -and (
        $_.CommandLine -like "*spring-boot:run*" -or
        $_.CommandLine -like "*ConsumoEspertoApplication*" -or
        $_.CommandLine -like "*plexus.classworlds.launcher.Launcher*spring-boot*" -or
        $_.CommandLine -like "*ng.js*serve*"
    )
}

foreach ($p in $procs) {
    Write-Host "Encerrando PID $($p.ProcessId) ($($p.Name)) [repo]..."
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 1
Write-Host "Concluido."
