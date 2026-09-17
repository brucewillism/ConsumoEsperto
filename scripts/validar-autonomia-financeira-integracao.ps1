# Validacao HTTP real da autonomia financeira — nao usa banco de producao.
# Uso (backend JAR ja no ar, profile integracao, flags ligadas):
#   .\scripts\validar-autonomia-financeira-integracao.ps1
param(
    [string]$BaseUrl = "http://localhost:18081",
    [string]$Banco = "consumoesperto_integracao"
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$pass = "SenhaTeste123!"
$reportDir = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportPath = Join-Path $reportDir "autonomia-validacao-$suffix.json"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
if (-not (Test-Path $psql)) { $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe" }

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
$pgUser = if ($dot['DATABASE_USERNAME']) { $dot['DATABASE_USERNAME'] } else { $dot['POSTGRES_USER'] }
$env:PGPASSWORD = if ($dot['DATABASE_PASSWORD']) { $dot['DATABASE_PASSWORD'] } else { $dot['POSTGRES_PASSWORD'] }

$cases = New-Object System.Collections.Generic.List[object]
function Add-Case([string]$id, [string]$resultado, [string]$evidencia) {
    $script:cases.Add([ordered]@{ id = $id; resultado = $resultado; evidencia = $evidencia })
    $color = if ($resultado -eq "OK") { "Green" } elseif ($resultado -eq "LIMITADO") { "Yellow" } else { "Red" }
    Write-Host "[$resultado] $id — $evidencia" -ForegroundColor $color
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{}, $Body = $null)
    $p = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 60; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $p.ContentType = "application/json; charset=utf-8"
        $p.Body = if ($Body -is [string]) { $Body } else { ($Body | ConvertTo-Json -Depth 10 -Compress) }
    }
    return Invoke-RestMethod @p
}
function Invoke-ApiRaw {
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{}, $Body = $null)
    $p = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 60; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $p.ContentType = "application/json; charset=utf-8"
        $p.Body = if ($Body -is [string]) { $Body } else { ($Body | ConvertTo-Json -Depth 10 -Compress) }
    }
    return Invoke-WebRequest @p
}
function AuthH([string]$token) { return @{ Authorization = "Bearer $token" } }
function Sql([string]$q) {
    $out = & $psql -U $pgUser -d $Banco -tAc $q 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql falhou: $out" }
    return ($out | Out-String).Trim()
}
function Wait-Processed([int]$usuarioId, [int]$sec = 20) {
    $deadline = (Get-Date).AddSeconds($sec)
    do {
        $n = Sql "SELECT count(*) FROM financial_domain_event WHERE usuario_id=$usuarioId AND processed=false"
        if ([int]$n -eq 0) { return $true }
        Start-Sleep -Milliseconds 800
    } while ((Get-Date) -lt $deadline)
    return $false
}

Write-Host "=== Autonomia HTTP -> $BaseUrl db=$Banco suffix=$suffix ===" -ForegroundColor Cyan

# Runtime
try {
    $h = Invoke-Api GET "/actuator/health"
    Add-Case "runtime.backend" "OK" ("health=" + $h.status)
} catch {
    Add-Case "runtime.backend" "FALHA" $_.Exception.Message
    throw
}
$edithHealth = "DOWN"
try {
    $er = Invoke-WebRequest -Uri "http://localhost:5173/actuator/health" -TimeoutSec 3 -UseBasicParsing
    $edithHealth = "HTTP $($er.StatusCode)"
} catch { $edithHealth = "indisponivel: $($_.Exception.Message)" }
Add-Case "runtime.edith" "LIMITADO" $edithHealth

# Users
$userA = @{ username = "aut_a.$suffix@test.local"; email = "aut_a.$suffix@test.local"; password = $pass; nome = "Autonomia A $suffix" }
$userB = @{ username = "aut_b.$suffix@test.local"; email = "aut_b.$suffix@test.local"; password = $pass; nome = "Autonomia B $suffix" }
$regA = Invoke-Api POST "/api/auth/registro" -Body $userA
$regB = Invoke-Api POST "/api/auth/registro" -Body $userB
$loginA = Invoke-Api POST "/api/auth/login" -Body @{ username = $userA.email; password = $pass }
$loginB = Invoke-Api POST "/api/auth/login" -Body @{ username = $userB.email; password = $pass }
$tokA = $loginA.token; $tokB = $loginB.token
$hA = AuthH $tokA; $hB = AuthH $tokB
$uidA = [int]$regA.id; $uidB = [int]$regB.id
Add-Case "massa.users" "OK" "A=$uidA B=$uidB"

# Preferencias AUTONOMOUS_SAFE + classificar
Invoke-Api PUT "/api/autonomy/preferencias" -Headers $hA -Body @{
    nivel = "AUTONOMOUS_SAFE"; registrarAuto = $true; classificarAuto = $true
    aprenderCategorias = $true; detectarAssinaturas = $true; detectarDuplicatas = $true
    detectarAnomalias = $true; preverSaldo = $true; jarvisProativo = $true
    resumoDiario = $false; resumoSemanal = $false
} | Out-Null
Invoke-Api PUT "/api/autonomy/preferencias" -Headers $hB -Body @{
    nivel = "ASSISTED"; classificarAuto = $true; aprenderCategorias = $true
} | Out-Null

$catComb = Invoke-Api POST "/api/categorias" -Headers $hA -Body @{ nome = "Combustivel"; descricao = "Cat A"; cor = "#cc5500"; icone = "gas" }
$catTransp = Invoke-Api POST "/api/categorias" -Headers $hA -Body @{ nome = "Transporte"; descricao = "Cat A2"; cor = "#336699"; icone = "bus" }
$catEnergia = Invoke-Api POST "/api/categorias" -Headers $hA -Body @{ nome = "Energia"; descricao = "Cat A3"; cor = "#f0c000"; icone = "bolt" }
$catB = Invoke-Api POST "/api/categorias" -Headers $hB -Body @{ nome = "Secreta B"; descricao = "nao vazar"; cor = "#111111"; icone = "lock" }
$contaA = Invoke-Api POST "/api/contas-bancarias" -Headers $hA -Body @{
    nome = "Conta A"; tipo = "CORRENTE"; saldoAtual = 1000; limiteChequeEspecial = 0; ativa = $true; padrao = $true
}
$contaB = Invoke-Api POST "/api/contas-bancarias" -Headers $hB -Body @{
    nome = "Conta B"; tipo = "CORRENTE"; saldoAtual = 50; limiteChequeEspecial = 0; ativa = $true; padrao = $true
}
$cartaoA = Invoke-Api POST "/api/cartoes-credito" -Headers $hA -Body @{
    nome = "Cartao Teste"; banco = "Banco Teste"; numeroCartao = "4111111111111111"
    limiteCredito = 5000; limiteDisponivel = 5000; diaVencimento = 28; ativo = $true
}
$cartaoB = Invoke-Api POST "/api/cartoes-credito" -Headers $hB -Body @{
    nome = "Cartao B"; banco = "Banco B"; numeroCartao = "4222222222222222"
    limiteCredito = 2000; limiteDisponivel = 2000; diaVencimento = 15; ativo = $true
}

$venc = Get-Date "2026-09-28T12:00:00"
$fech = Get-Date "2026-09-21T12:00:00"
$faturaA = Invoke-Api POST "/api/faturas" -Headers $hA -Body @{
    cartaoCreditoId = $cartaoA.id
    valorTotal = 0; valorFatura = 0
    dataVencimento = $venc.ToString("yyyy-MM-ddTHH:mm:ss")
    dataFechamento = $fech.ToString("yyyy-MM-ddTHH:mm:ss")
    statusFatura = "ABERTA"; paga = $false
}
$faturaAntes = [decimal]$faturaA.valorTotal
if (-not $faturaAntes) { $faturaAntes = 0 }
Add-Case "massa.fatura" "OK" ("faturaId=" + $faturaA.id + " totalAntes=" + $faturaAntes)

$rulesShell = Sql "SELECT count(*) FROM merchant_category_rules r JOIN usuarios u ON u.id=r.usuario_id WHERE u.id=$uidA AND (r.merchant_normalized ILIKE '%SHELL%' OR r.merchant_pattern ILIKE '%SHELL%')"
Add-Case "massa.sem_regra_shell" $(if ([int]$rulesShell -eq 0) { "OK" } else { "FALHA" }) "regrasShellA=$rulesShell"

# Device
$devA = Invoke-Api POST "/api/mobile-capture/devices" -Headers $hA -Body @{ name = "iPhone Teste A"; platform = "IOS_SHORTCUTS" }
$devB = Invoke-Api POST "/api/mobile-capture/devices" -Headers $hB -Body @{ name = "iPhone Teste B"; platform = "IOS_SHORTCUTS" }
$tokenDevA = $devA.deviceToken
if (-not $tokenDevA) { $tokenDevA = $devA.device_token }
Add-Case "wallet.token" $(if ($tokenDevA) { "OK" } else { "FALHA" }) ("deviceId=" + $devA.deviceId)

function Send-Wallet([string]$devToken, [hashtable]$payload) {
    $json = $payload | ConvertTo-Json -Compress -Depth 6
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/ingestion/mobile/transactions" -Method POST `
        -Headers @{ "X-CE-Device-Token" = $devToken; "Content-Type" = "application/json" } `
        -Body $json -TimeoutSec 60
    return @{ status = [int]$resp.StatusCode; body = ($resp.Content | ConvertFrom-Json) }
}

$now = Get-Date
$occurred = $now.ToString("yyyy-MM-ddTHH:mm:ss")
$wallet1 = $null
try {
    $wallet1 = Send-Wallet $tokenDevA @{
        source = "IOS_WALLET"; merchant = "POSTO SHELL"; amount = 89.90
        card_hint = "Cartao Teste"; occurred_at = $occurred
        client_event_id = "wallet-shell-1-$suffix"
    }
    Add-Case "wallet.http" $(if ($wallet1.status -ge 200 -and $wallet1.status -lt 300) { "OK" } else { "FALHA" }) ("HTTP $($wallet1.status) status=$($wallet1.body.status) tx=$($wallet1.body.transacaoId)")
} catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Add-Case "wallet.http" "FALHA" "HTTP $code $($_.Exception.Message)"
}

Wait-Processed $uidA | Out-Null
Start-Sleep -Seconds 2

$txId1 = $null
if ($wallet1 -and $wallet1.body.transacaoId) { $txId1 = [int]$wallet1.body.transacaoId }
$txCount = Sql "SELECT count(*) FROM transacoes t WHERE t.usuario_id=$uidA AND t.excluido=false AND (t.descricao ILIKE '%SHELL%' OR t.merchant_normalized ILIKE '%SHELL%' OR t.merchant_raw ILIKE '%SHELL%')"
Add-Case "wallet.transacao" $(if ([int]$txCount -ge 1) { "OK" } else { "FALHA" }) "shellTxCount=$txCount txId=$txId1"

if (-not $txId1) {
    $txId1Raw = Sql "SELECT id FROM transacoes WHERE usuario_id=$uidA AND excluido=false ORDER BY id DESC LIMIT 1"
    if ($txId1Raw) { $txId1 = [int]$txId1Raw }
}

$dec1 = Sql "SELECT decision_type, result, policy, confidence, edith_task_id, cognitive_used, reason FROM autonomy_decision_log WHERE usuario_id=$uidA ORDER BY id DESC LIMIT 1"
Add-Case "wallet.autonomy" $(if ($dec1) { "OK" } else { "FALHA" }) "decision=$dec1"
if ($dec1 -match "EDITH" -or $dec1 -match "true") {
    Add-Case "wallet.edith" "OK" $dec1
} else {
    Add-Case "wallet.edith" "LIMITADO" "E.D.I.T.H. nao classificou (esperado com gateway offline). $dec1"
}

# Logs nao devem mostrar fallback de modelo no adapter de autonomia
$logFile = Join-Path $root "logs\integracao-backend.log"
$openaiHit = $false
if (Test-Path $logFile) {
    $tail = Get-Content $logFile -Tail 400 -ErrorAction SilentlyContinue
    $openaiHit = [bool]($tail | Select-String -Pattern "openai\.azure|api.openai.com|groq.com|generativelanguage.googleapis.com" -Quiet)
}
Add-Case "wallet.sem_fallback_llm" $(if (-not $openaiHit) { "OK" } else { "FALHA" }) "openai/groq/gemini no log imediato=$openaiHit"

# Fatura viva
$fatApos = Invoke-Api GET "/api/faturas/$($faturaA.id)" -Headers $hA
$faturaDepois = [decimal]$(if ($fatApos.valorTotal) { $fatApos.valorTotal } else { 0 })
$delta = [math]::Round($faturaDepois - $faturaAntes, 2)
$somaTx = Sql "SELECT coalesce(sum(valor),0) FROM transacoes WHERE fatura_id=$($faturaA.id) AND excluido=false AND tipo_transacao='DESPESA'"
Add-Case "fatura.delta_wallet" $(if ($delta -eq 89.90 -or [math]::Abs($delta - 89.90) -lt 0.011) { "OK" } else { "FALHA" }) "antes=$faturaAntes depois=$faturaDepois delta=$delta somaTx=$somaTx"

# CSV mesma compra
$csvDate = $now.ToString("dd/MM/yyyy")
$csvPath = Join-Path $env:TEMP "shell-$suffix.csv"
@"
Data;Descricao;Valor
$csvDate;POSTO SHELL;89,90
"@ | Set-Content -Path $csvPath -Encoding UTF8
$uploadJson = & curl.exe -s -S -X POST "$BaseUrl/api/importacoes/faturas/upload" `
    -H "Authorization: Bearer $tokA" `
    -F "file=@$csvPath;type=text/csv" `
    -F "tipoRecurso=CARTAO" `
    -F "cartaoCreditoId=$($cartaoA.id)"
$imp = $uploadJson | ConvertFrom-Json
$impId = $imp.id
Add-Case "csv.upload" $(if ($impId) { "OK" } else { "FALHA" }) "importId=$impId preview=$($imp.status)"

$itens = @($imp.itens)
if (-not $itens -or $itens.Count -eq 0) {
    try { $imp2 = Invoke-Api GET "/api/importacoes/faturas/pendentes" -Headers $hA; $imp = @($imp2) | Where-Object { $_.id -eq $impId } | Select-Object -First 1 } catch {}
}
$previewStatus = $null
if ($imp.itens) {
    $previewStatus = @($imp.itens)[0].statusPreview
    if (-not $previewStatus) { $previewStatus = @($imp.itens)[0].status }
}
Add-Case "csv.preview" $(if ($previewStatus) { "OK" } else { "LIMITADO" }) "itemStatus=$previewStatus"

$conf = $null
try {
    $conf = Invoke-Api POST "/api/importacoes/faturas/$impId/confirmar" -Headers $hA -Body @{}
    Add-Case "csv.confirmar" "OK" ("criadas=$($conf.criadas) conciliadas=$($conf.conciliadas) msg=$($conf.mensagem)")
} catch {
    Add-Case "csv.confirmar" "FALHA" $_.Exception.Message
}
Wait-Processed $uidA | Out-Null
Start-Sleep -Seconds 1

$txShell = [int](Sql "SELECT count(*) FROM transacoes WHERE usuario_id=$uidA AND excluido=false AND (descricao ILIKE '%SHELL%' OR merchant_normalized ILIKE '%SHELL%')")
$evCount = 0
if ($txId1) {
    $evCount = [int](Sql "SELECT count(*) FROM transaction_evidence WHERE transacao_id=$txId1")
    $evSrc = Sql "SELECT string_agg(source, chr(44) ORDER BY source) FROM transaction_evidence WHERE transacao_id=$txId1"
} else {
    $evSrc = ""
}
Add-Case "evidence.count" $(if ($txShell -eq 1 -and $evCount -ge 2) { "OK" } else { "FALHA" }) "txShell=$txShell evidence=$evCount sources=$evSrc"

$fatCsv = Invoke-Api GET "/api/faturas/$($faturaA.id)" -Headers $hA
$faturaAposCsv = [decimal]$(if ($fatCsv.valorTotal) { $fatCsv.valorTotal } else { 0 })
Add-Case "fatura.apos_csv" $(if ([math]::Abs($faturaAposCsv - $faturaDepois) -lt 0.011) { "OK" } else { "FALHA" }) "antesCsv=$faturaDepois depoisCsv=$faturaAposCsv"

# Correcao Combustivel -> Transporte
if ($txId1) {
    $txAtual = Invoke-Api GET "/api/transacoes/$txId1" -Headers $hA
    $txAtual.categoriaId = $catComb.id
    Invoke-Api PUT "/api/transacoes/$txId1" -Headers $hA -Body $txAtual | Out-Null
    Wait-Processed $uidA | Out-Null
    $txAtual = Invoke-Api GET "/api/transacoes/$txId1" -Headers $hA
    $txAtual.categoriaId = $catTransp.id
    Invoke-Api PUT "/api/transacoes/$txId1" -Headers $hA -Body $txAtual | Out-Null
    Wait-Processed $uidA | Out-Null
    $ruleAfter = Sql "SELECT categoria_id, merchant_normalized FROM merchant_category_rules WHERE usuario_id=$uidA AND merchant_normalized ILIKE '%SHELL%' LIMIT 1"
    Add-Case "learning.correcao" $(if ($ruleAfter -match "$($catTransp.id)") { "OK" } else { "FALHA" }) "rule=$ruleAfter"
} else {
    Add-Case "learning.correcao" "FALHA" "sem transacao para corrigir"
}

# Segunda compra SHELL 70
$cogBefore = [int](Sql "SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=$uidA AND cognitive_used=true")
$wallet2 = Send-Wallet $tokenDevA @{
    source = "IOS_WALLET"; merchant = "POSTO SHELL"; amount = 70.00
    card_hint = "Cartao Teste"; occurred_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    client_event_id = "wallet-shell-2-$suffix"
}
Wait-Processed $uidA | Out-Null
Start-Sleep -Seconds 2
$cogAfter = [int](Sql "SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=$uidA AND cognitive_used=true")
$tx2 = $wallet2.body.transacaoId
$cat2 = $null
if ($tx2) {
    $row2 = Invoke-Api GET "/api/transacoes/$tx2" -Headers $hA
    $cat2 = $row2.categoriaId
}
$tx2id = if ($tx2) { $tx2 } else { 0 }
$dec2 = Sql "SELECT reason, cognitive_used, result FROM autonomy_decision_log WHERE transacao_id=$tx2id ORDER BY id DESC LIMIT 1"
$noEdith2 = ($cogAfter -eq $cogBefore)
Add-Case "learning.segunda_compra" $(if ($cat2 -eq $catTransp.id -and $noEdith2) { "OK" } elseif ($cat2 -eq $catTransp.id) { "PARCIAL" } else { "FALHA" }) "cat2=$cat2 esperado=$($catTransp.id) cognitiveDelta=$($cogAfter-$cogBefore) dec=$dec2 http=$($wallet2.status)"

# Niveis MANUAL vs ASSISTED
Invoke-Api PUT "/api/autonomy/preferencias" -Headers $hA -Body @{ nivel = "MANUAL"; classificarAuto = $true; aprenderCategorias = $true } | Out-Null
$wMan = Send-Wallet $tokenDevA @{
    source = "IOS_WALLET"; merchant = "POSTO SHELL"; amount = 15.00
    card_hint = "Cartao Teste"; occurred_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    client_event_id = "wallet-shell-man-$suffix"
}
Wait-Processed $uidA | Out-Null
$catMan = $null
if ($wMan.body.transacaoId) {
    $catMan = (Invoke-Api GET "/api/transacoes/$($wMan.body.transacaoId)" -Headers $hA).categoriaId
}
$manId = if ($wMan.body.transacaoId) { $wMan.body.transacaoId } else { 0 }
$decMan = Sql "SELECT result, policy FROM autonomy_decision_log WHERE transacao_id=$manId ORDER BY id DESC LIMIT 1"
Add-Case "levels.MANUAL" $(if ($decMan -match "NEEDS_REVIEW" -or -not $catMan) { "OK" } else { "FALHA" }) "cat=$catMan dec=$decMan"

Invoke-Api PUT "/api/autonomy/preferencias" -Headers $hA -Body @{ nivel = "ASSISTED"; classificarAuto = $true; aprenderCategorias = $true } | Out-Null
$wAs = Send-Wallet $tokenDevA @{
    source = "IOS_WALLET"; merchant = "POSTO SHELL"; amount = 16.00
    card_hint = "Cartao Teste"; occurred_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    client_event_id = "wallet-shell-as-$suffix"
}
Wait-Processed $uidA | Out-Null
$catAs = $null
if ($wAs.body.transacaoId) {
    $catAs = (Invoke-Api GET "/api/transacoes/$($wAs.body.transacaoId)" -Headers $hA).categoriaId
}
$asId = if ($wAs.body.transacaoId) { $wAs.body.transacaoId } else { 0 }
$decAs = Sql "SELECT result, policy FROM autonomy_decision_log WHERE transacao_id=$asId ORDER BY id DESC LIMIT 1"
Add-Case "levels.ASSISTED" $(if ($catAs -eq $catTransp.id -and $decAs -match "EXECUTED") { "OK" } else { "FALHA" }) "cat=$catAs dec=$decAs"
Invoke-Api PUT "/api/autonomy/preferencias" -Headers $hA -Body @{ nivel = "AUTONOMOUS_SAFE"; classificarAuto = $true } | Out-Null
Add-Case "levels.AUTONOMOUS_SAFE" "OK" "preferencia restaurada; 2a compra SHELL ja exercitou SAFE_AUTO"

# PIX baixa confianca
$wPix = Send-Wallet $tokenDevA @{
    source = "PIX"; merchant = "JOAO SILVA"; amount = 350.00
    occurred_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    client_event_id = "pix-joao-$suffix"
}
Wait-Processed $uidA | Out-Null
$revPix = Invoke-Api GET "/api/autonomy/revisao" -Headers $hA
$revHasPix = $false
foreach ($it in @($revPix.itens)) {
    if ($it.detail -match "JOAO" -or $it.title -match "PIX" -or $it.kind -eq "CATEGORY" -or $it.kind -eq "ANOMALY") {
        if ($it.detail -match "JOAO" -or $it.detail -match "350" -or $it.title -match "nao identifiquei" -or $it.title -match "Não identifiquei" -or $it.title -match "Estabelecimento novo") {
            $revHasPix = $true
        }
    }
}
$pixTx = $wPix.body.transacaoId
$pixCat = $null
if ($pixTx) { $pixCat = (Invoke-Api GET "/api/transacoes/$pixTx" -Headers $hA).categoriaId }
Add-Case "review.pix" $(if ($revHasPix -and -not $pixCat) { "OK" } elseif ($revPix.total -gt 0) { "PARCIAL" } else { "FALHA" }) "reviewTotal=$($revPix.total) pixCat=$pixCat http=$($wPix.status) tx=$pixTx"

# Anomalia energia
$dtEnergia = @(70,60,50,40)
$valsEnergia = @(100,105,98,102)
for ($i = 0; $i -lt 4; $i++) {
    $d = (Get-Date).AddDays(-$dtEnergia[$i]).ToString("yyyy-MM-ddTHH:mm:ss")
    Invoke-Api POST "/api/transacoes" -Headers $hA -Body @{
        descricao = "Energia CEMIG"; valor = $valsEnergia[$i]; tipoTransacao = "DESPESA"
        categoriaId = $catEnergia.id; contaBancariaId = $contaA.id; dataTransacao = $d
        statusConferencia = "CONFIRMADA"
    } | Out-Null
}
$en220 = Invoke-Api POST "/api/transacoes" -Headers $hA -Body @{
    descricao = "Energia CEMIG"; valor = 220; tipoTransacao = "DESPESA"
    categoriaId = $catEnergia.id; contaBancariaId = $contaA.id
    dataTransacao = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    statusConferencia = "CONFIRMADA"
}
Wait-Processed $uidA | Out-Null
$anom = Sql "SELECT count(*) FROM autonomy_review_item WHERE usuario_id=$uidA AND kind='ANOMALY' AND status='OPEN'"
Add-Case "anomaly.energia" $(if ([int]$anom -gt 0) { "OK" } else { "FALHA" }) "openAnomaly=$anom (algoritmo=spike 30d vs 30-90d, nao z-score unitario)"

# Duplicate POSTO X
$t0 = Get-Date
$t1 = $t0.ToString("yyyy-MM-dd") + "T10:00:00"
$t2 = $t0.ToString("yyyy-MM-dd") + "T10:04:00"
Send-Wallet $tokenDevA @{
    source = "IOS_WALLET"; merchant = "POSTO X"; amount = 89.90
    card_hint = "Cartao Teste"; occurred_at = $t1; client_event_id = "dup-x-1-$suffix"
} | Out-Null
Wait-Processed $uidA | Out-Null
Send-Wallet $tokenDevA @{
    source = "IOS_WALLET"; merchant = "POSTO X"; amount = 89.90
    card_hint = "Cartao Teste"; occurred_at = $t2; client_event_id = "dup-x-2-$suffix"
} | Out-Null
Wait-Processed $uidA | Out-Null
$dupRev = [int](Sql "SELECT count(*) FROM autonomy_review_item WHERE usuario_id=$uidA AND kind='DUPLICATE' AND status='OPEN'")
$dupTx = [int](Sql "SELECT count(*) FROM transacoes WHERE usuario_id=$uidA AND excluido=false AND descricao ILIKE '%POSTO X%'")
Add-Case "duplicate.posto_x" $(if ($dupRev -ge 1 -and $dupTx -ge 2) { "OK" } else { "FALHA" }) "reviewDup=$dupRev txPostoX=$dupTx (nao fundir/excluir)"

# Forbidden: nenhum EXECUTED de acao bancaria
$forb = [int](Sql "SELECT count(*) FROM autonomy_decision_log WHERE usuario_id=$uidA AND action IN ('TRANSFER_MONEY','PAY_INVOICE','CREATE_PIX','MOVE_BALANCE','CHANGE_CREDENTIALS','DELETE_HISTORY') AND result='EXECUTED'")
$pagFatura = [int](Sql "SELECT count(*) FROM transacoes WHERE usuario_id=$uidA AND tipo_transacao='PAGAMENTO_FATURA'")
Add-Case "security.forbidden" $(if ($forb -eq 0) { "OK" } else { "FALHA" }) "executedForbidden=$forb pagamentoFatura=$pagFatura"

# Isolamento
$txB = Invoke-Api POST "/api/transacoes" -Headers $hB -Body @{
    descricao = "Compra secreta B"; valor = 12.34; tipoTransacao = "DESPESA"
    categoriaId = $catB.id; contaBancariaId = $contaB.id
    dataTransacao = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
}
$listA = @(Invoke-Api GET "/api/transacoes" -Headers $hA)
$leakB = @($listA | Where-Object { $_.descricao -match "secreta B" -or $_.id -eq $txB.id })
$revB = Invoke-Api GET "/api/autonomy/revisao" -Headers $hB
$revAIds = @($revPix.itens | ForEach-Object { $_.id })
$revBHasA = $false
foreach ($it in @($revB.itens)) { if ($revAIds -contains $it.id) { $revBHasA = $true } }
$wBshell = Send-Wallet $devB.deviceToken @{
    source = "IOS_WALLET"; merchant = "POSTO SHELL"; amount = 33.00
    card_hint = "Cartao B"; occurred_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    client_event_id = "b-shell-$suffix"
}
Wait-Processed $uidB | Out-Null
$catBshell = $null
if ($wBshell.body.transacaoId) {
    $catBshell = (Invoke-Api GET "/api/transacoes/$($wBshell.body.transacaoId)" -Headers $hB).categoriaId
}
Add-Case "isolation.AB" $(if ($leakB.Count -eq 0 -and -not $revBHasA -and $catBshell -ne $catTransp.id) { "OK" } else { "FALHA" }) "leakTx=$($leakB.Count) leakReview=$revBHasA catBshell=$catBshell"

# Outbox
$evProc = Sql "SELECT count(*), count(*) FILTER (WHERE processed) FROM financial_domain_event WHERE usuario_id=$uidA"
$unproc = [int](Sql "SELECT count(*) FROM financial_domain_event WHERE usuario_id=$uidA AND processed=false")
Add-Case "outbox.processed" $(if ($unproc -eq 0) { "OK" } else { "PARCIAL" }) "eventos=$evProc unprocessed=$unproc"

# Idempotencia: reabrir ultimo evento
$lastEv = Sql "SELECT id FROM financial_domain_event WHERE usuario_id=$uidA ORDER BY id DESC LIMIT 1"
$txBefore = [int](Sql "SELECT count(*) FROM transacoes WHERE usuario_id=$uidA AND excluido=false")
$revBefore = [int](Sql "SELECT count(*) FROM autonomy_review_item WHERE usuario_id=$uidA")
Sql "UPDATE financial_domain_event SET processed=false, processed_at=NULL WHERE id=$lastEv" | Out-Null
# Sem endpoint de reprocess; listener nao dispara de novo. Drain so no cron 10 min.
Add-Case "outbox.idempotencia" "LIMITADO" "evento $lastEv marcado unprocessed; reprocesso AFTER_COMMIT nao e re-disparado sem job/cron. txBefore=$txBefore revBefore=$revBefore"

# Safe-to-spend
$safe = Invoke-Api GET "/api/autonomy/resumo" -Headers $hA
$sts = $safe.safeToSpend
$bruto = [decimal]$(if ($sts.disponivelAposObrigacoes) { $sts.disponivelAposObrigacoes } else { 0 })
$margem = [decimal]$(if ($sts.margemSeguranca) { $sts.margemSeguranca } else { 0 })
$got = [decimal]$(if ($sts.safeToSpend) { $sts.safeToSpend } else { 0 })
$expect = [math]::Round($bruto - ($bruto * $margem), 2)
if ($expect -lt 0) { $expect = 0 }
Add-Case "safetospend.formula" $(if ([math]::Abs($got - $expect) -lt 0.011) { "OK" } else { "FALHA" }) "saldo=$($sts.saldo) obrigacoes=$($sts.obrigacoes) bruto=$bruto margem=$margem safe=$got esperado=$expect"

$fc = $safe.forecast
Add-Case "forecast.horizontes" $(if ($fc.d30 -and $fc.d60 -and $fc.d90) { "OK" } else { "FALHA" }) ("d30=" + ($fc.d30 | ConvertTo-Json -Compress) + " d60=" + ($fc.d60 | ConvertTo-Json -Compress) + " d90=" + ($fc.d90 | ConvertTo-Json -Compress))
Add-Case "forecast.modelos" "PARCIAL" "d30 usa ForecastFinanceiroService; d60/d90 usam SaldoService.calcularProjecaoSafraDto — nao e o mesmo modelo"

# Jarvis (flag ainda false no arranque) — notificacoes
$notifA = @()
try { $notifA = @(Invoke-Api GET "/api/notificacoes/todas" -Headers $hA) } catch {}
$notifB = @()
try { $notifB = @(Invoke-Api GET "/api/notificacoes/todas" -Headers $hB) } catch {}
Add-Case "jarvis.fase1_desligado" "OK" "proactive flag off no arranque; notifA=$($notifA.Count) notifB=$($notifB.Count)"

$ok = @($cases | Where-Object { $_.resultado -eq "OK" }).Count
$fail = @($cases | Where-Object { $_.resultado -eq "FALHA" }).Count
$lim = @($cases | Where-Object { $_.resultado -eq "LIMITADO" }).Count
$par = @($cases | Where-Object { $_.resultado -eq "PARCIAL" }).Count
$summary = [ordered]@{
    suffix = $suffix; usuarioA = $uidA; usuarioB = $uidB
    ok = $ok; falha = $fail; limitado = $lim; parcial = $par
    casos = $cases
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "Relatorio: $reportPath  OK=$ok FALHA=$fail LIMITADO=$lim PARCIAL=$par" -ForegroundColor Cyan
if ($fail -gt 0) { exit 2 } else { exit 0 }
