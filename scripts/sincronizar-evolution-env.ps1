# Atualiza tools/evolution-api/.env com DATABASE_CONNECTION_URI alinhado ao ConsumoEsperto
# (base separada evolution_api no mesmo PostgreSQL).
# Configuracao de IA / WhatsApp por usuario fica no PostgreSQL (usuario_ai_config), nao neste script.
#
# Ordem de precedencia para user/senha:
#   1) Variaveis de ambiente DATABASE_USERNAME, DATABASE_PASSWORD
#   2) Ficheiro backend/config/db-local.properties (copie de db-local.properties.example)
#   3) Padrao: bruce + senha vazia (auth trust local)
#
# Uso: powershell -File scripts/sincronizar-evolution-env.ps1
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$evoEnv = Join-Path $root "tools\evolution-api\.env"
$propsFile = Join-Path $root "backend\config\db-local.properties"

if (-not (Test-Path $evoEnv)) {
    throw "Nao encontrado: $evoEnv - clone a Evolution em tools\evolution-api primeiro."
}

$user = $env:DATABASE_USERNAME
$pass = $env:DATABASE_PASSWORD
$hostDb = "127.0.0.1"
$port = "5432"
$dbName = "evolution_api"

if (Test-Path $propsFile) {
    Get-Content $propsFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $k, $v = $_ -split '=', 2
        $k = $k.Trim()
        $v = $v.Trim()
        switch ($k) {
            "DATABASE_USERNAME" { if (-not $user) { $user = $v } }
            "DATABASE_PASSWORD" { if ($null -eq $env:DATABASE_PASSWORD) { $pass = $v } }
            "DATABASE_HOST" { $hostDb = $v }
            "DATABASE_PORT" { $port = $v }
            "EVOLUTION_DATABASE" { $dbName = $v }
        }
    }
}

if (-not $user) { $user = "bruce" }
if ($null -eq $pass) { $pass = "" }

$userEnc = [uri]::EscapeDataString($user)
$passEnc = [uri]::EscapeDataString($pass)
if ($pass) {
    $uri = "postgresql://${userEnc}:${passEnc}@${hostDb}:${port}/${dbName}?schema=public"
} else {
    $uri = "postgresql://${userEnc}@${hostDb}:${port}/${dbName}?schema=public"
}

$content = Get-Content $evoEnv -Raw -Encoding UTF8
$content = $content -replace "(?m)^DATABASE_CONNECTION_URI=.*", "DATABASE_CONNECTION_URI=$uri"
$content = $content -replace "(?m)^CHATWOOT_IMPORT_DATABASE_CONNECTION_URI=.*", "CHATWOOT_IMPORT_DATABASE_CONNECTION_URI=$uri"
# Stack local ConsumoEsperto: Evolution na 8080; Spring (dev-evolution) na 8081 — webhooks devem bater no Spring.
# 127.0.0.1 evita IPv6 ::1 vs IPv4 em alguns stacks Node→Java no Windows
$springWebhook = "http://127.0.0.1:8081/api/public/evolution/webhook"
if ($content -match "(?m)^SERVER_PORT=") {
    $content = $content -replace "(?m)^SERVER_PORT=.*", "SERVER_PORT=8080"
} else {
    $content = $content.TrimEnd() + "`r`nSERVER_PORT=8080`r`n"
}
if ($content -match "(?m)^SERVER_URL=") {
    $content = $content -replace "(?m)^SERVER_URL=.*", "SERVER_URL=http://localhost:8080"
} else {
    $content = $content.TrimEnd() + "`r`nSERVER_URL=http://localhost:8080`r`n"
}
if ($content -match "(?m)^WEBHOOK_GLOBAL_URL=") {
    $content = $content -replace "(?m)^WEBHOOK_GLOBAL_URL=.*", "WEBHOOK_GLOBAL_URL=$springWebhook"
} else {
    $content = $content.TrimEnd() + "`r`nWEBHOOK_GLOBAL_URL=$springWebhook`r`n"
}
# Persistencia em PostgreSQL (Evolution v2 usa DATABASE_PROVIDER; DATABASE_TYPE=memory e legado)
if ($content -match "(?m)^DATABASE_PROVIDER=") {
    $content = $content -replace "(?m)^DATABASE_PROVIDER=.*", "DATABASE_PROVIDER=postgresql"
} else {
    $content = $content.TrimEnd() + "`r`nDATABASE_PROVIDER=postgresql`r`n"
}
if ($content -match "(?m)^DATABASE_TYPE=\s*memory\s*$") {
    $content = $content -replace "(?m)^DATABASE_TYPE=\s*memory\s*$", "DATABASE_TYPE=postgres"
}
# Nao sobrescrever AUTHENTICATION_API_KEY existente (Spring usa evolution.apikey com o mesmo valor).
if ($content -notmatch "(?m)^AUTHENTICATION_API_KEY=") {
    $content = $content.TrimEnd() + "`r`nAUTHENTICATION_API_KEY=42abc123`r`n"
}

[System.IO.File]::WriteAllText($evoEnv, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Atualizado $evoEnv"
Write-Host "DATABASE_CONNECTION_URI=$uri"

$m = [regex]::Match($content, '(?m)^AUTHENTICATION_API_KEY=(.*)$')
if ($m.Success -and $m.Groups[1].Value.Trim()) {
    $authKey = $m.Groups[1].Value.Trim()
    $secretsDir = Join-Path $root "backend\config"
    if (-not (Test-Path $secretsDir)) { New-Item -ItemType Directory -Path $secretsDir | Out-Null }
    $secretsPath = Join-Path $secretsDir "application-dev-evolution-secrets.properties"
    $sec = @"
# Gerado por sincronizar-evolution-env.ps1 (nao commitar). Sobrescreve evolution.apikey para igualar AUTHENTICATION_API_KEY da Evolution.
# URL da API Evolution (Node) — porta 8080; o Spring com dev-evolution corre na 8081.
evolution.url=http://localhost:8080
evolution.apikey=$authKey
evolution.instance=ConsumoEsperto
"@
    [System.IO.File]::WriteAllText($secretsPath, $sec.TrimEnd() + "`r`n", [System.Text.UTF8Encoding]::new($false))
    Write-Host "Escrito $secretsPath (alinhado ao .env da Evolution)"
}
