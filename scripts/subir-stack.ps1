# Sobe SEMPRE: Evolution em Node (clone + npm se preciso) + backend 8081 + frontend 4200.
# Java/Maven: tools\ na sessao do backend.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $root

function Test-EvolutionNodeDir {
    param([string]$Dir)
    return (Test-Path (Join-Path $Dir "package.json"))
}

function Ensure-EvolutionClone {
    param([string]$EvoDir)
    if (Test-EvolutionNodeDir $EvoDir) { return $true }
    $parent = Split-Path $EvoDir -Parent
    $leaf = Split-Path $EvoDir -Leaf
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    if (Test-Path $EvoDir) {
        Write-Warning "Pasta existe mas sem package.json: $EvoDir — corrija ou apague a pasta para clonar de novo."
        return (Test-EvolutionNodeDir $EvoDir)
    }
    $git = Get-Command git -ErrorAction SilentlyContinue
    if (-not $git) {
        Write-Warning "Git nao encontrado — nao foi possivel clonar Evolution em tools\evolution-api."
        return $false
    }
    Write-Host "[Evolution] Clonando EvolutionAPI/evolution-api em $EvoDir ..."
    Push-Location $parent
    try {
        & git clone --depth 1 https://github.com/EvolutionAPI/evolution-api.git $leaf
    } finally {
        Pop-Location
    }
    return (Test-EvolutionNodeDir $EvoDir)
}

function Prepare-EvolutionNode {
    param([string]$EvoDir)
    $node = $null
    $toolsNode = Join-Path $root "tools\node\node.exe"
    if (Test-Path $toolsNode) { $node = $toolsNode }
    else {
        $n = Get-Command node -ErrorAction SilentlyContinue
        if ($n) { $node = $n.Source }
    }
    if (-not $node) {
        throw "Node nao encontrado para npm install/build da Evolution. Instale Node ou preencha tools\node."
    }
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        Push-Location $EvoDir
        if (-not (Test-Path "node_modules")) {
            Write-Host "[Evolution] npm install (primeira vez pode demorar)..."
            if ($node -like "*.exe") {
                $env:PATH = "$(Split-Path $node -Parent);" + $env:PATH
            }
            & npm install
        }
        if (-not (Test-Path "dist\main.js")) {
            Write-Host "[Evolution] npm run build..."
            & npm run build
            if (-not (Test-Path "dist\main.js")) {
                Write-Warning "Build da Evolution nao gerou dist\main.js. Configure .env (Postgres/Redis) conforme doc oficial e rode npm run build na pasta."
            }
        }
    } finally {
        Pop-Location
        $ErrorActionPreference = $old
    }
}

$evoDir = $env:EVOLUTION_DIR
if (-not $evoDir) { $evoDir = Join-Path $root "tools\evolution-api" }

[void](Ensure-EvolutionClone $evoDir)
$evolutionNode = Test-EvolutionNodeDir $evoDir
$evolutionSubiu = $false

if ($evolutionNode) {
    Write-Host "[1/3] Evolution API (Node) em: $evoDir"
    $evoEnvFile = Join-Path $evoDir ".env"
    if (Test-Path $evoEnvFile) {
        & (Join-Path $root "scripts\sincronizar-evolution-env.ps1")
    }
    Prepare-EvolutionNode $evoDir
    $env:EVOLUTION_DIR = $evoDir
    Start-Process -FilePath "cmd.exe" -ArgumentList @(
        "/k", "cd /d `"$root`" && call rodar-evolution.bat"
    ) -WindowStyle Normal
    $evolutionSubiu = $true
    Write-Host "Evolution (Node): http://localhost:8080"
    Write-Host "Webhook -> Spring: http://localhost:8081/api/public/evolution/webhook ou http://localhost:8081/api/whatsapp/webhook"
} else {
    throw "Evolution obrigatoria: instale Git para clonar tools\evolution-api ou defina EVOLUTION_DIR apontando para uma Evolution API Node valida."
}

if (-not $evolutionSubiu) {
    throw "Evolution nao iniciou."
}

Start-Sleep -Seconds 5

Write-Host '[2/3] Backend (8081, dev-evolution, tools\java)...'
Start-Process -FilePath "cmd.exe" -ArgumentList @(
    "/k", "cd /d `"$root`" && call rodar-backend-evolution.bat"
) -WindowStyle Normal

Start-Sleep -Seconds 8

Write-Host "[3/3] Frontend (4200)..."
Start-Process -FilePath "cmd.exe" -ArgumentList @(
    "/k", "cd /d `"$root`" && call rodar-frontend.bat"
) -WindowStyle Normal

Write-Host 'Pronto. Evolution:8080 | API Spring:8081 | Angular:4200'
