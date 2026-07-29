# 13 cenarios runtime Motor Financeiro — PostgreSQL + backend JAR
param(
    [string]$BaseUrl = "http://localhost:18081",
    [string]$Relatorio = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$pass = "SenhaTeste123!"
$results = @()
$ano = (Get-Date).Year
$mes = (Get-Date).Month
$dt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")

function Reg-User([string]$tag) {
    $email = "motor.$tag.$suffix@test.local"
    $body = @{ username = $email; email = $email; password = $pass; nome = "Motor $tag" }
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/registro" -Method Post -Body ($body | ConvertTo-Json) -ContentType "application/json" | Out-Null
    $tok = (Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body (@{ username = $email; password = $pass } | ConvertTo-Json) -ContentType "application/json").token
    return @{ email = $email; headers = @{ Authorization = "Bearer $tok" } }
}

function New-CatContaCartao($h) {
    $cat = Invoke-RestMethod -Uri "$BaseUrl/api/categorias" -Method Post -Headers $h.headers `
        -Body (@{ nome = "Cat $suffix"; descricao = ""; cor = "#336699"; icone = "tag" } | ConvertTo-Json) -ContentType "application/json"
    $conta = Invoke-RestMethod -Uri "$BaseUrl/api/contas-bancarias" -Method Post -Headers $h.headers `
        -Body (@{ nome = "Conta"; tipo = "CORRENTE"; saldoAtual = 5000; limiteChequeEspecial = 0; ativa = $true; padrao = $true } | ConvertTo-Json) -ContentType "application/json"
    $cartao = Invoke-RestMethod -Uri "$BaseUrl/api/cartoes-credito" -Method Post -Headers $h.headers `
        -Body (@{ nome = "Cartao"; banco = "Teste"; numeroCartao = "4111111111111111"; limiteCredito = 1000; limiteDisponivel = 1000; diaVencimento = 10; ativo = $true } | ConvertTo-Json) -ContentType "application/json"
    return @{ cat = $cat; conta = $conta; cartao = $cartao }
}

function Add-Tx($h, $catId, $contaId, $tipo, $valor, $cartaoId = $null) {
    $b = @{
        descricao = "Tx $tipo $valor"; valor = $valor; tipoTransacao = $tipo
        categoriaId = $catId; dataTransacao = $dt; statusConferencia = "CONFIRMADA"
    }
    if ($cartaoId) { $b.cartaoCreditoId = $cartaoId; $b.Remove("contaBancariaId") }
    else { $b.contaBancariaId = $contaId }
    Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $h.headers -Body ($b | ConvertTo-Json) -ContentType "application/json" | Out-Null
}

function Invoke-Motor($h) {
    return Invoke-RestMethod -Uri "$BaseUrl/api/motor-financeiro?narrativa=false" -Headers $h.headers -TimeoutSec 45
}

function Invoke-Rel($h) {
    return Invoke-RestMethod -Uri "$BaseUrl/api/relatorios/mensal?ano=$ano&mes=$mes" -Headers $h.headers -TimeoutSec 30
}

function Assert-Motor([string]$cenario, $h, [decimal]$espRec, [decimal]$espDesp, [scriptblock]$extra = $null) {
    $rel = Invoke-Rel $h
    $m = Invoke-Motor $h
    $json = $m | ConvertTo-Json -Depth 12 -Compress
    $bad = ($json -match '(?i)(?<![\w])NaN(?![\w])|(?<![\w])Infinity(?![\w])|(?<![\w])undefined(?![\w])')
    $rec = [decimal]$rel.totalReceitas
    $desp = [decimal]$rel.totalDespesas
    $okRel = ([math]::Abs($rec - $espRec) -lt 0.02) -and ([math]::Abs($desp - $espDesp) -lt 0.02)
    $okMotor = ($null -ne $m.scoreExplicavel) -and ($null -ne $m.forecastInteligente) -and (-not $bad)
    $okExtra = $true
    if ($extra) { $okExtra = & $extra $m $rel }
    $alertas = @($m.scoreExplicavel.componentes).Count
    if ($null -eq $alertas) { $alertas = 0 }
    $script:results += [ordered]@{
        cenario = $cenario
        receitaEsperada = $espRec; despesaEsperada = $espDesp
        receitaObtida = $rec; despesaObtida = $desp
        alertas = $alertas
        resultado = $(if ($okRel -and $okMotor -and $okExtra) { "OK" } else { "FALHA" })
    }
}

Write-Host "Motor 13 cenarios -> $BaseUrl" -ForegroundColor Cyan

# 1 usuario sem dados
$h1 = Reg-User "sem_dados"
Assert-Motor "01_usuario_sem_dados" $h1 0 0

# 2 somente receita
$h2 = Reg-User "so_receita"
$ctx2 = New-CatContaCartao $h2
Add-Tx $h2 $ctx2.cat.id $ctx2.conta.id "RECEITA" 800
Assert-Motor "02_somente_receita" $h2 800 0

# 3 somente despesa
$h3 = Reg-User "so_despesa"
$ctx3 = New-CatContaCartao $h3
Add-Tx $h3 $ctx3.cat.id $ctx3.conta.id "DESPESA" 350
Assert-Motor "03_somente_despesa" $h3 0 350

# 4 saldo positivo
$h4 = Reg-User "saldo_pos"
$ctx4 = New-CatContaCartao $h4
Add-Tx $h4 $ctx4.cat.id $ctx4.conta.id "RECEITA" 1000
Add-Tx $h4 $ctx4.cat.id $ctx4.conta.id "DESPESA" 200
Assert-Motor "04_saldo_positivo" $h4 1000 200

# 5 saldo negativo
$h5 = Reg-User "saldo_neg"
$ctx5 = New-CatContaCartao $h5
Add-Tx $h5 $ctx5.cat.id $ctx5.conta.id "RECEITA" 100
Add-Tx $h5 $ctx5.cat.id $ctx5.conta.id "DESPESA" 600
Assert-Motor "05_saldo_negativo" $h5 100 600

# 6 conta saldo baixo
$h6 = Reg-User "conta_baixa"
$ctx6 = New-CatContaCartao $h6
Invoke-RestMethod -Uri "$BaseUrl/api/contas-bancarias/$($ctx6.conta.id)" -Method Put -Headers $h6.headers `
    -Body (@{ nome = "Conta"; tipo = "CORRENTE"; saldoAtual = 50; limiteChequeEspecial = 0; ativa = $true; padrao = $true } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Add-Tx $h6 $ctx6.cat.id $ctx6.conta.id "DESPESA" 40
Assert-Motor "06_conta_saldo_baixo" $h6 0 40 { param($m,$r) $true }

# 7 cartao proximo limite
$h7 = Reg-User "cartao_limite"
$ctx7 = New-CatContaCartao $h7
Invoke-RestMethod -Uri "$BaseUrl/api/cartoes-credito/$($ctx7.cartao.id)" -Method Put -Headers $h7.headers `
    -Body (@{ nome = "Cartao"; banco = "Teste"; numeroCartao = "4111111111111111"; limiteCredito = 1000; limiteDisponivel = 150; diaVencimento = 10; ativo = $true } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Add-Tx $h7 $ctx7.cat.id $ctx7.conta.id "DESPESA" 850 $ctx7.cartao.id
Assert-Motor "07_cartao_proximo_limite" $h7 0 850

# 8 cartao acima limite (comprometimento > limite via transacao)
$h8 = Reg-User "cartao_acima"
$ctx8 = New-CatContaCartao $h8
Invoke-RestMethod -Uri "$BaseUrl/api/cartoes-credito/$($ctx8.cartao.id)" -Method Put -Headers $h8.headers `
    -Body (@{ nome = "Cartao"; banco = "Teste"; numeroCartao = "4111111111111111"; limiteCredito = 500; limiteDisponivel = 0; diaVencimento = 10; ativo = $true } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Add-Tx $h8 $ctx8.cat.id $ctx8.conta.id "DESPESA" 600 $ctx8.cartao.id
Assert-Motor "08_cartao_acima_limite" $h8 0 600

# 9 orcamento excedido
$h9 = Reg-User "orc_exced"
$ctx9 = New-CatContaCartao $h9
Invoke-RestMethod -Uri "$BaseUrl/api/orcamentos" -Method Post -Headers $h9.headers `
    -Body (@{ categoriaId = $ctx9.cat.id; valorLimite = 100; mes = $mes; ano = $ano } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Add-Tx $h9 $ctx9.cat.id $ctx9.conta.id "DESPESA" 250
Assert-Motor "09_orcamento_excedido" $h9 0 250

# 10 fatura aberta (transacao cartao)
$h10 = Reg-User "fatura_aberta"
$ctx10 = New-CatContaCartao $h10
Add-Tx $h10 $ctx10.cat.id $ctx10.conta.id "DESPESA" 120 $ctx10.cartao.id
$faturas = Invoke-RestMethod -Uri "$BaseUrl/api/faturas" -Headers $h10.headers
Assert-Motor "10_fatura_aberta" $h10 0 120 { param($m,$r) ($faturas.Count -ge 1) }

# 11 agendamento futuro
$h11 = Reg-User "ag_futuro"
$ctx11 = New-CatContaCartao $h11
Invoke-RestMethod -Uri "$BaseUrl/api/agendamentos-pagamentos" -Method Post -Headers $h11.headers `
    -Body (@{ contaDebitoId = $ctx11.conta.id; beneficiario = "Futuro"; valor = 99; dataVencimento = (Get-Date).AddDays(14).ToString("yyyy-MM-dd") } | ConvertTo-Json) -ContentType "application/json" | Out-Null
Assert-Motor "11_agendamento_futuro" $h11 0 0

# 12 dados incompletos (conta sem transacao, sem renda)
$h12 = Reg-User "incompleto"
New-CatContaCartao $h12 | Out-Null
Assert-Motor "12_dados_incompletos" $h12 0 0

# 13 isolamento usuario B
$hA = Reg-User "iso_a"
$hB = Reg-User "iso_b"
$ctxA = New-CatContaCartao $hA
Add-Tx $hA $ctxA.cat.id $ctxA.conta.id "DESPESA" 777
$mB = Invoke-Motor $hB
$jsonB = $mB | ConvertTo-Json -Compress
$iso = -not ($jsonB -match "777")
$results += [ordered]@{
    cenario = "13_isolamento_usuario_b"
    receitaEsperada = 0; despesaEsperada = 0
    receitaObtida = 0; despesaObtida = 0
    alertas = @($mB.scoreExplicavel.componentes).Count
    resultado = $(if ($iso) { "OK" } else { "FALHA" })
}

$fail = ($results | Where-Object { $_.resultado -eq "FALHA" }).Count
if (-not $Relatorio) { $Relatorio = Join-Path $root "logs\motor-integracao-$suffix.json" }
New-Item -ItemType Directory -Force -Path (Split-Path $Relatorio -Parent) | Out-Null
@{ timestamp = (Get-Date).ToString("o"); cenarios = $results; total = $results.Count; aprovados = $results.Count - $fail; falhas = $fail } | ConvertTo-Json -Depth 5 | Set-Content $Relatorio -Encoding UTF8
Write-Host "Motor: $($results.Count - $fail)/$($results.Count) OK -> $Relatorio" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
if ($fail -gt 0) { exit 1 }
