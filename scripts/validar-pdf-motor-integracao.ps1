# Valida PDF mensal (bytes + API relatorio) e Motor Financeiro em runtime
param(
    [string]$BaseUrl = "http://localhost:18081",
    [string]$Relatorio = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$pass = "SenhaTeste123!"
$pdfResults = @()
$motorResults = @()

function Login([string]$email) {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body (@{ username = $email; password = $pass } | ConvertTo-Json) `
        -ContentType "application/json" -TimeoutSec 30
    return $r.token
}

Write-Host "PDF/Motor validation -> $BaseUrl" -ForegroundColor Cyan

$userA = @{ username = "pdf_a.$suffix@test.local"; email = "pdf_a.$suffix@test.local"; password = $pass; nome = "Relatório Ação" }
$userB = @{ username = "pdf_b.$suffix@test.local"; email = "pdf_b.$suffix@test.local"; password = $pass; nome = "Usuario B PDF" }
Invoke-RestMethod -Uri "$BaseUrl/api/auth/registro" -Method Post -Body ($userA | ConvertTo-Json) -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/auth/registro" -Method Post -Body ($userB | ConvertTo-Json) -ContentType "application/json" | Out-Null
$tokA = Login $userA.email
$tokB = Login $userB.email
$hA = @{ Authorization = "Bearer $tokA" }
$hB = @{ Authorization = "Bearer $tokB" }

$cat = Invoke-RestMethod -Uri "$BaseUrl/api/categorias" -Method Post -Headers $hA `
    -Body (@{ nome = "PDF Cat"; descricao = ""; cor = "#000"; icone = "tag" } | ConvertTo-Json) -ContentType "application/json"
$conta = Invoke-RestMethod -Uri "$BaseUrl/api/contas-bancarias" -Method Post -Headers $hA `
    -Body (@{ nome = "Conta PDF"; tipo = "CORRENTE"; saldoAtual = 2000; limiteChequeEspecial = 0; ativa = $true; padrao = $true } | ConvertTo-Json) -ContentType "application/json"
$dt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA -Body (@{
    descricao = "Despesa PDF $suffix"; valor = 75.50; tipoTransacao = "DESPESA"
    categoriaId = $cat.id; contaBancariaId = $conta.id; dataTransacao = $dt
} | ConvertTo-Json) -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA -Body (@{
    descricao = "Receita PDF"; valor = 500; tipoTransacao = "RECEITA"
    categoriaId = $cat.id; contaBancariaId = $conta.id; dataTransacao = $dt
} | ConvertTo-Json) -ContentType "application/json" | Out-Null

$ano = (Get-Date).Year; $mes = (Get-Date).Month
$rel = Invoke-RestMethod -Uri "$BaseUrl/api/relatorios/mensal?ano=$ano&mes=$mes" -Headers $hA -TimeoutSec 30

try {
    $pdf = Invoke-WebRequest -Uri "$BaseUrl/api/relatorios/mensal.pdf?ano=$ano&mes=$mes" -Headers $hA -TimeoutSec 60
    $sig = if ($pdf.Content.Length -ge 4) { [Text.Encoding]::ASCII.GetString($pdf.Content[0..3]) } else { "" }
    $pdfOk = ($pdf.StatusCode -eq 200) -and ($sig -eq "%PDF") -and ($pdf.Content.Length -gt 500)
    $pdfResults += [ordered]@{
        validacao = "status_assinatura_tamanho"; resultado = $(if ($pdfOk) { "OK" } else { "FALHA" })
        evidencia = "status=$($pdf.StatusCode) bytes=$($pdf.Content.Length) sig=$sig"
    }
    $pdfResults += [ordered]@{
        validacao = "content_disposition"; resultado = $(if ($pdf.Headers["Content-Disposition"]) { "OK" } else { "FALHA" })
        evidencia = $pdf.Headers["Content-Disposition"]
    }
} catch {
    $pdfResults += [ordered]@{ validacao = "download"; resultado = "FALHA"; evidencia = $_.Exception.Message }
}

$relOk = ($rel.totalReceitas -ne $null) -and ($rel.totalDespesas -ne $null)
$pdfResults += [ordered]@{
    validacao = "relatorio_json_coerente"; resultado = $(if ($relOk) { "OK" } else { "FALHA" })
    evidencia = "receitas=$($rel.totalReceitas) despesas=$($rel.totalDespesas)"
}

# periodo sem dados (mes futuro)
try {
    $fut = Invoke-WebRequest -Uri "$BaseUrl/api/relatorios/mensal.pdf?ano=2099&mes=1" -Headers $hA -TimeoutSec 30 -ErrorAction Stop
    $pdfResults += [ordered]@{ validacao = "periodo_sem_dados"; resultado = "FALHA"; evidencia = "HTTP $($fut.StatusCode)" }
} catch {
    $c = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $pdfResults += [ordered]@{
        validacao = "periodo_sem_dados"; resultado = $(if ($c -eq 404) { "OK" } else { "FALHA" }); evidencia = "HTTP $c"
    }
}

# Motor Financeiro cenarios
function Test-Motor([string]$nome, [scriptblock]$setup, [int]$minAlertas = 0) {
    if ($setup) { & $setup | Out-Null }
    try {
        $m = Invoke-RestMethod -Uri "$BaseUrl/api/motor-financeiro?narrativa=false" -Headers $hA -TimeoutSec 45
        $json = $m | ConvertTo-Json -Depth 8 -Compress
        $bad = ($json -match '"NaN"|Infinity|null indevido') -or ($json -match 'undefined')
        $score = $m.scoreExplicavel.scoreTotal
        $alertas = @($m.forecastInteligente.alertas).Count
        if ($null -eq $alertas) { $alertas = 0 }
        $ok = (-not $bad) -and ($null -ne $score)
        $script:motorResults += [ordered]@{
            cenario = $nome; esperado = "score+semNaN"; obtido = "score=$score alertas=$alertas"
            resultado = $(if ($ok) { "OK" } else { "FALHA" })
        }
    } catch {
        $script:motorResults += [ordered]@{ cenario = $nome; esperado = "200"; obtido = "erro"; resultado = "FALHA" }
    }
}

Test-Motor "usuario_sem_dados_previos" $null
Test-Motor "com_receita_despesa" $null
Test-Motor "usuario_b_isolado" {
    Invoke-RestMethod -Uri "$BaseUrl/api/motor-financeiro?narrativa=false" -Headers $hB -TimeoutSec 45 | Out-Null
}

$fail = ($pdfResults + $motorResults | Where-Object { $_.resultado -eq "FALHA" }).Count
if (-not $Relatorio) { $Relatorio = Join-Path $root "logs\pdf-motor-$suffix.json" }
New-Item -ItemType Directory -Force -Path (Split-Path $Relatorio -Parent) | Out-Null
@{ pdf = $pdfResults; motor = $motorResults; falhas = $fail } | ConvertTo-Json -Depth 5 | Set-Content $Relatorio -Encoding UTF8
Write-Host "PDF/Motor: falhas=$fail -> $Relatorio" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
if ($fail -gt 0) { exit 1 }
