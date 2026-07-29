# Smoke HTTP integral — metricas claras (2xx vs 4xx esperado vs falha)
param(
    [string]$BaseUrl = "http://localhost:18081",
    [string]$Relatorio = "",
    [switch]$BancoLimpo,
    [switch]$PararNoPrimeiroErro
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$pass = "SenhaTeste123!"

$results = New-Object System.Collections.Generic.List[object]
$tokens = @{}
$ids = @{}
$metrics = @{
    fluxosExecutados = 0
    requisicoesExecutadas = 0
    respostas2xx = 0
    respostas4xx = 0
    respostas5xx = 0
    categorias2xxEsperadas = 0
    categorias4xxEsperadas = 0
    categoriasAusentesEsperadas = 0
    fluxosAprovados = 0
    resultadosInesperados = 0
    falhasInesperadas = 0
}

function Add-Result([int]$num, [string]$fluxo, [string]$metodo, [string]$endpoint,
    [ValidateSet('2xx','4xx','ausente')][string]$categoriaEsperada,
    [int]$statusEsperado, $statusObtido, [string]$resultado, [string]$nota = "") {
    $script:metrics.fluxosExecutados++
    if ($resultado -in @('OK','ESPERADO')) { $script:metrics.fluxosAprovados++ }
    if ($resultado -eq 'FALHA') { $script:metrics.falhasInesperadas++; $script:metrics.resultadosInesperados++ }
    if ($categoriaEsperada -eq '2xx') { $script:metrics.categorias2xxEsperadas++ }
    elseif ($categoriaEsperada -eq '4xx') { $script:metrics.categorias4xxEsperadas++ }
    elseif ($categoriaEsperada -eq 'ausente') { $script:metrics.categoriasAusentesEsperadas++ }
    if ($categoriaEsperada -ne 'ausente' -and $statusObtido -gt 0) {
        $script:metrics.requisicoesExecutadas++
        if ($statusObtido -ge 200 -and $statusObtido -lt 300) { $script:metrics.respostas2xx++ }
        elseif ($statusObtido -ge 400 -and $statusObtido -lt 500) { $script:metrics.respostas4xx++ }
        elseif ($statusObtido -ge 500) { $script:metrics.respostas5xx++ }
    }
    $script:results.Add([ordered]@{
        num = $num; fluxo = $fluxo; metodo = $metodo; endpoint = $endpoint
        categoriaEsperada = $categoriaEsperada; statusEsperado = $statusEsperado
        statusObtido = $statusObtido; resultado = $resultado; nota = $nota
    }) | Out-Null
}

function Auth([string]$k) {
    if ($tokens[$k]) { return @{ Authorization = "Bearer $($tokens[$k])" } }
    return @{}
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{}, $Body = $null)
    $p = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 45; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $p.ContentType = "application/json"
        $p.Body = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 }
    }
    return Invoke-WebRequest @p
}

Write-Host "Smoke -> $BaseUrl (suffix=$suffix)" -ForegroundColor Cyan

# 1 health
try {
    $r = Invoke-Api GET "/actuator/health"
    Add-Result 1 "health" GET "/actuator/health" "2xx" 200 $r.StatusCode $(if ($r.StatusCode -eq 200) { "OK" } else { "FALHA" })
} catch {
    Add-Result 1 "health" GET "/actuator/health" "2xx" 200 0 "FALHA"
    if ($PararNoPrimeiroErro) { throw "Health falhou" }
}

$userA = @{ username = "a.$suffix@test.local"; email = "a.$suffix@test.local"; password = $pass; nome = "Usuario A $suffix" }
$userB = @{ username = "b.$suffix@test.local"; email = "b.$suffix@test.local"; password = $pass; nome = "Usuario B $suffix" }

foreach ($pair in @(@(2,"A",$userA), @(3,"B",$userB))) {
    $n=$pair[0]; $lbl=$pair[1]; $u=$pair[2]
    try {
        $r = Invoke-Api POST "/api/auth/registro" -Body $u
        Add-Result $n "registro_$lbl" POST "/api/auth/registro" "2xx" 200 $r.StatusCode "OK"
    } catch {
        $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        Add-Result $n "registro_$lbl" POST "/api/auth/registro" "2xx" 200 $c "FALHA"
        if ($PararNoPrimeiroErro) { throw }
    }
}

foreach ($pair in @(@(4,"A",$userA), @(5,"B",$userB))) {
    $n=$pair[0]; $lbl=$pair[1]; $u=$pair[2]
    try {
        $login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
            -Body (@{ username = $u.email; password = $u.password } | ConvertTo-Json) `
            -ContentType "application/json" -TimeoutSec 30
        $tokens[$lbl] = $login.token
        Add-Result $n "login_$lbl" POST "/api/auth/login" "2xx" 200 200 "OK"
    } catch {
        $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        Add-Result $n "login_$lbl" POST "/api/auth/login" "2xx" 200 $c "FALHA"
        if ($PararNoPrimeiroErro) { throw }
    }
}

# 6 refresh token — AUSENTE POR DECISAO DE ARQUITETURA (AuthController: login + registro apenas)
Add-Result 6 "refresh_token" GET "/api/auth/refresh" "ausente" 0 0 "ESPERADO" "AUSENTE_POR_DECISAO_DE_ARQUITETURA"

$hA = Auth "A"
$hB = Auth "B"

if ($tokens["A"]) {
    try {
        $c = Invoke-RestMethod -Uri "$BaseUrl/api/categorias" -Method Post -Headers $hA `
            -Body (@{ nome = "Cat $suffix"; descricao = "Smoke"; cor = "#336699"; icone = "tag" } | ConvertTo-Json) `
            -ContentType "application/json"
        $ids.categoria = $c.id
        Add-Result 7 "categoria" POST "/api/categorias" "2xx" 200 200 "OK"
    } catch { Add-Result 7 "categoria" POST "/api/categorias" "2xx" 200 0 "FALHA" }

    try {
        $cb = Invoke-RestMethod -Uri "$BaseUrl/api/contas-bancarias" -Method Post -Headers $hA `
            -Body (@{ nome = "Conta $suffix"; tipo = "CORRENTE"; saldoAtual = 1000; limiteChequeEspecial = 0; ativa = $true; padrao = $true } | ConvertTo-Json) `
            -ContentType "application/json"
        $ids.conta = $cb.id
        Add-Result 8 "conta" POST "/api/contas-bancarias" "2xx" 200 200 "OK"
    } catch { Add-Result 8 "conta" POST "/api/contas-bancarias" "2xx" 200 0 "FALHA" }

    if ($ids.conta) {
        try {
            $cc = Invoke-RestMethod -Uri "$BaseUrl/api/cartoes-credito" -Method Post -Headers $hA `
                -Body (@{ nome = "Cartao $suffix"; banco = "Banco Teste"; numeroCartao = "4111111111111111"; limiteCredito = 5000; limiteDisponivel = 5000; diaVencimento = 17; ativo = $true } | ConvertTo-Json) `
                -ContentType "application/json"
            $ids.cartao = $cc.id
            Add-Result 9 "cartao" POST "/api/cartoes-credito" "2xx" 200 200 "OK"
        } catch { Add-Result 9 "cartao" POST "/api/cartoes-credito" "2xx" 200 0 "FALHA" }
    }

    if ($ids.categoria -and $ids.conta) {
        try {
            $t = Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA `
                -Body (@{
                    descricao = "Despesa smoke $suffix"; valor = 50; tipoTransacao = "DESPESA"
                    categoriaId = $ids.categoria; contaBancariaId = $ids.conta
                    dataTransacao = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
                } | ConvertTo-Json) -ContentType "application/json"
            $ids.transacao = $t.id
            Add-Result 10 "transacao" POST "/api/transacoes" "2xx" 200 200 "OK"
        } catch { Add-Result 10 "transacao" POST "/api/transacoes" "2xx" 200 0 "FALHA" }

        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA `
                -Body (@{
                    descricao = "Parcelada smoke $suffix"; valor = 100; tipoTransacao = "DESPESA"
                    categoriaId = $ids.categoria; contaBancariaId = $ids.conta
                    grupoParcelaId = "SMK-$suffix"; parcelaAtual = 1; totalParcelas = 3
                    dataTransacao = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
                } | ConvertTo-Json) -ContentType "application/json" | Out-Null
            Add-Result 11 "transacao_parcelada" POST "/api/transacoes" "2xx" 200 200 "OK"
        } catch { Add-Result 11 "transacao_parcelada" POST "/api/transacoes" "2xx" 200 0 "FALHA" }
    }

    $ano = (Get-Date).Year; $mes = (Get-Date).Month
    $qs = "/api/relatorios/mensal?ano=$ano" + '&mes=' + $mes
    try {
        $r = Invoke-Api GET $qs -Headers $hA
        Add-Result 12 "relatorio_mensal" GET $qs "2xx" 200 $r.StatusCode $(if ($r.StatusCode -eq 200) { "OK" } else { "FALHA" })
    } catch { Add-Result 12 "relatorio_mensal" GET $qs "2xx" 200 0 "FALHA" }

    $ini = (Get-Date).AddMonths(-1).ToString("yyyy-MM-dd")
    $fim = (Get-Date).ToString("yyyy-MM-dd")
    $csvPath = "/api/exportacao/csv/transacoes?dataInicio=$ini" + '&dataFim=' + $fim
    try {
        $r = Invoke-Api GET $csvPath -Headers $hA
        $ok = ($r.StatusCode -eq 200 -and $r.RawContentLength -gt 0)
        Add-Result 13 "csv" GET $csvPath "2xx" 200 $r.StatusCode $(if ($ok) { "OK" } else { "FALHA" })
    } catch { Add-Result 13 "csv" GET $csvPath "2xx" 200 0 "FALHA" }

    $pdfPath = "/api/relatorios/mensal.pdf?ano=$ano" + '&mes=' + $mes
    try {
        $r = Invoke-Api GET $pdfPath -Headers $hA
        $sig = if ($r.Content.Length -ge 4) { [Text.Encoding]::ASCII.GetString($r.Content[0..3]) } else { "" }
        Add-Result 14 "pdf_mensal" GET $pdfPath "2xx" 200 $r.StatusCode $(if ($sig -eq "%PDF") { "OK" } else { "FALHA" })
    } catch { Add-Result 14 "pdf_mensal" GET $pdfPath "2xx" 200 0 "FALHA" }

    try {
        $r = Invoke-Api GET "/api/motor-financeiro" -Headers $hA
        Add-Result 15 "motor_financeiro" GET "/api/motor-financeiro" "2xx" 200 $r.StatusCode "OK"
    } catch { Add-Result 15 "motor_financeiro" GET "/api/motor-financeiro" "2xx" 200 0 "FALHA" }

    if ($ids.conta) {
        $ag = @{
            contaDebitoId = $ids.conta; beneficiario = "Benef $suffix"; valor = 25.50
            dataVencimento = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
        }
        if ($ids.categoria) { $ag.categoriaId = $ids.categoria }
        try {
            $agd = Invoke-RestMethod -Uri "$BaseUrl/api/agendamentos-pagamentos" -Method Post -Headers $hA -Body ($ag | ConvertTo-Json) -ContentType "application/json"
            $ids.ag = $agd.id
            Add-Result 16 "agendamento_criar" POST "/api/agendamentos-pagamentos" "2xx" 200 200 "OK"
        } catch { Add-Result 16 "agendamento_criar" POST "/api/agendamentos-pagamentos" "2xx" 200 0 "FALHA" }

        if ($ids.ag) {
            $ag2 = $ag.Clone(); $ag2.beneficiario = "Benef edit $suffix"; $ag2.valor = 30
            foreach ($step in @(
                @(17,"agendamento_editar","PUT","/api/agendamentos-pagamentos/$($ids.ag)",$ag2),
                @(18,"agendamento_pausar","POST","/api/agendamentos-pagamentos/$($ids.ag)/pausar",$null),
                @(19,"agendamento_ativar","POST","/api/agendamentos-pagamentos/$($ids.ag)/ativar",$null),
                @(20,"agendamento_executar","POST","/api/agendamentos-pagamentos/$($ids.ag)/executar",$null)
            )) {
                try {
                    if ($step[1] -eq "agendamento_editar") { Invoke-Api $step[2] $step[3] -Headers $hA -Body $step[4] | Out-Null }
                    else { Invoke-Api $step[2] $step[3] -Headers $hA | Out-Null }
                    Add-Result $step[0] $step[1] $step[2] $step[3] "2xx" 200 200 "OK"
                } catch { Add-Result $step[0] $step[1] $step[2] $step[3] "2xx" 200 0 "FALHA" }
            }
            try {
                Invoke-Api GET "/api/agendamentos-pagamentos/historico" -Headers $hA | Out-Null
                Add-Result 21 "agendamento_historico" GET "/api/agendamentos-pagamentos/historico" "2xx" 200 200 "OK"
            } catch { Add-Result 21 "agendamento_historico" GET "/api/agendamentos-pagamentos/historico" "2xx" 200 0 "FALHA" }

            try {
                $agCancel = $ag.Clone(); $agCancel.beneficiario = "Cancel $suffix"; $agCancel.valor = 10
                $agC = Invoke-RestMethod -Uri "$BaseUrl/api/agendamentos-pagamentos" -Method Post -Headers $hA -Body ($agCancel | ConvertTo-Json) -ContentType "application/json"
                Invoke-Api DELETE "/api/agendamentos-pagamentos/$($agC.id)" -Headers $hA | Out-Null
                Add-Result 22 "agendamento_cancelar" DELETE "/api/agendamentos-pagamentos/{id}" "2xx" 200 200 "OK"
            } catch { Add-Result 22 "agendamento_cancelar" DELETE "/api/agendamentos-pagamentos/{id}" "2xx" 200 0 "FALHA" }
        }
    }
}

try {
    Invoke-Api POST "/api/auth/registro" -Body $userA | Out-Null
    Add-Result 23 "registro_duplicado" POST "/api/auth/registro" "4xx" 409 200 "FALHA"
} catch {
    $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    Add-Result 23 "registro_duplicado" POST "/api/auth/registro" "4xx" 409 $c $(if ($c -eq 409) { "ESPERADO" } else { "FALHA" })
}

try {
    Invoke-Api GET "/api/categorias" | Out-Null
    Add-Result 24 "sem_token" GET "/api/categorias" "4xx" 401 200 "FALHA"
} catch {
    $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    Add-Result 24 "sem_token" GET "/api/categorias" "4xx" 401 $c $(if ($c -eq 401) { "ESPERADO" } else { "FALHA" })
}

try {
    Invoke-Api GET "/api/categorias" -Headers @{ Authorization = "Bearer invalido.smoke" } | Out-Null
    Add-Result 25 "token_invalido" GET "/api/categorias" "4xx" 401 200 "FALHA"
} catch {
    $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    Add-Result 25 "token_invalido" GET "/api/categorias" "4xx" 401 $c $(if ($c -eq 401) { "ESPERADO" } else { "FALHA" })
}

$report = [ordered]@{
    timestamp = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    suffix = $suffix
    metricas = $metrics
    resultados = $results
}
if (-not $Relatorio) { $Relatorio = Join-Path $root "logs\smoke-integracao-$suffix.json" }
New-Item -ItemType Directory -Force -Path (Split-Path $Relatorio -Parent) | Out-Null
$report | ConvertTo-Json -Depth 6 | Set-Content $Relatorio -Encoding UTF8

Write-Host ("Smoke: {0}/{1} aprovados | req={2} HTTP 2xx={3} 4xx={4} 5xx={5} | cat2xx={6} cat4xx={7} ausente={8} | inesperadas={9} -> {10}" -f `
    $metrics.fluxosAprovados, $metrics.fluxosExecutados, $metrics.requisicoesExecutadas, `
    $metrics.respostas2xx, $metrics.respostas4xx, $metrics.respostas5xx, `
    $metrics.categorias2xxEsperadas, $metrics.categorias4xxEsperadas, $metrics.categoriasAusentesEsperadas, `
    $metrics.resultadosInesperados, $Relatorio) `
    -ForegroundColor $(if ($metrics.falhasInesperadas -eq 0) { "Green" } else { "Red" })
if ($metrics.falhasInesperadas -gt 0) { exit 1 }
