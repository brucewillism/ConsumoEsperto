# Valida exportacao CSV — 15 casos com massa correta (cartao+fatura)
param(
    [string]$BaseUrl = "http://localhost:18081",
    [string]$Relatorio = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$pass = "SenhaTeste123!"
$results = @()

function Login([string]$email) {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body (@{ username = $email; password = $pass } | ConvertTo-Json) `
        -ContentType "application/json" -TimeoutSec 30
    return $r.token
}
function AuthH([string]$token) { return @{ Authorization = "Bearer $token" } }

function Parse-CsvBytes([byte[]]$bytes) {
    $text = if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        [Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3)
    } else { [Text.Encoding]::UTF8.GetString($bytes) }
    $lines = @(($text -split "`r?`n") | Where-Object { $_.Trim() -ne "" })
    if ($lines.Count -gt 0 -and $lines[0].StartsWith([char]0xFEFF)) { $lines[0] = $lines[0].TrimStart([char]0xFEFF) }
    return ,$lines
}

function Get-Csv([string]$token, [string]$qs) {
    $tmp = [IO.Path]::GetTempFileName()
    try {
        $resp = Invoke-WebRequest -Uri "$BaseUrl/api/exportacao/csv/transacoes$qs" -Headers (AuthH $token) -OutFile $tmp -TimeoutSec 45 -PassThru -ErrorAction Stop
        $bytes = [IO.File]::ReadAllBytes($tmp)
        return @{ status = 200; lines = (Parse-CsvBytes $bytes); hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF) }
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "HTTP $code"
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}

function Add-Row([string]$caso, [int]$linhasEsp, [int]$linhasObt, [bool]$ok, [bool]$isolamento) {
    $script:results += [ordered]@{
        caso = $caso; linhasEsperadas = $linhasEsp; linhasObtidas = $linhasObt
        conteudoCorreto = $(if ($ok) { "SIM" } else { "NAO" }); isolamento = $(if ($isolamento) { "OK" } else { "FALHA" })
        resultado = $(if ($ok -and $isolamento) { "OK" } else { "FALHA" })
    }
}

Write-Host "CSV validation (15 casos) -> $BaseUrl" -ForegroundColor Cyan

$userA = @{ username = "csv_a.$suffix@test.local"; email = "csv_a.$suffix@test.local"; password = $pass; nome = "CSV A" }
$userB = @{ username = "csv_b.$suffix@test.local"; email = "csv_b.$suffix@test.local"; password = $pass; nome = "CSV B" }
Invoke-RestMethod -Uri "$BaseUrl/api/auth/registro" -Method Post -Body ($userA | ConvertTo-Json) -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/auth/registro" -Method Post -Body ($userB | ConvertTo-Json) -ContentType "application/json" | Out-Null
$tokA = Login $userA.email
$tokB = Login $userB.email
$hA = AuthH $tokA

$catA = Invoke-RestMethod -Uri "$BaseUrl/api/categorias" -Method Post -Headers $hA `
    -Body (@{ nome = "Alimentacao $suffix"; descricao = "Cat A"; cor = "#336699"; icone = "tag" } | ConvertTo-Json) -ContentType "application/json"
$catB = Invoke-RestMethod -Uri "$BaseUrl/api/categorias" -Method Post -Headers (AuthH $tokB) `
    -Body (@{ nome = "Secreta B $suffix"; descricao = "Cat B"; cor = "#993366"; icone = "lock" } | ConvertTo-Json) -ContentType "application/json"
$contaA = Invoke-RestMethod -Uri "$BaseUrl/api/contas-bancarias" -Method Post -Headers $hA `
    -Body (@{ nome = "Conta A $suffix"; tipo = "CORRENTE"; saldoAtual = 5000; limiteChequeEspecial = 0; ativa = $true; padrao = $true } | ConvertTo-Json) -ContentType "application/json"
$contaB = Invoke-RestMethod -Uri "$BaseUrl/api/contas-bancarias" -Method Post -Headers (AuthH $tokB) `
    -Body (@{ nome = "Conta B $suffix"; tipo = "CORRENTE"; saldoAtual = 100; limiteChequeEspecial = 0; ativa = $true; padrao = $true } | ConvertTo-Json) -ContentType "application/json"
$cartaoA = Invoke-RestMethod -Uri "$BaseUrl/api/cartoes-credito" -Method Post -Headers $hA `
    -Body (@{ nome = "Cartao A"; banco = "Teste"; numeroCartao = "4111111111111111"; limiteCredito = 3000; limiteDisponivel = 3000; diaVencimento = 10; ativo = $true } | ConvertTo-Json) -ContentType "application/json"
$cartaoB = Invoke-RestMethod -Uri "$BaseUrl/api/cartoes-credito" -Method Post -Headers (AuthH $tokB) `
    -Body (@{ nome = "Cartao B"; banco = "Teste"; numeroCartao = "4222222222222222"; limiteCredito = 2000; limiteDisponivel = 2000; diaVencimento = 15; ativo = $true } | ConvertTo-Json) -ContentType "application/json"

$ini = (Get-Date).AddDays(-30).ToString("yyyy-MM-dd")
$fim = (Get-Date).ToString("yyyy-MM-dd")
$dt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
$txConta = "Despesa conta $suffix"
$txCartao = "Despesa cartao $suffix"
$txB = "Despesa secreta B $suffix"

Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA -Body (@{
    descricao = $txConta; valor = 50.25; tipoTransacao = "DESPESA"; statusConferencia = "CONFIRMADA"
    categoriaId = $catA.id; contaBancariaId = $contaA.id; dataTransacao = $dt
} | ConvertTo-Json) -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA -Body (@{
    descricao = $txCartao; valor = 75.00; tipoTransacao = "DESPESA"; statusConferencia = "PENDENTE"
    categoriaId = $catA.id; cartaoCreditoId = $cartaoA.id; dataTransacao = $dt
} | ConvertTo-Json) -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA -Body (@{
    descricao = "Receita A $suffix"; valor = 1000; tipoTransacao = "RECEITA"; statusConferencia = "CONFIRMADA"
    categoriaId = $catA.id; contaBancariaId = $contaA.id; dataTransacao = $dt
} | ConvertTo-Json) -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers (AuthH $tokB) -Body (@{
    descricao = $txB; valor = 99; tipoTransacao = "DESPESA"
    categoriaId = $catB.id; contaBancariaId = $contaB.id; dataTransacao = $dt
} | ConvertTo-Json) -ContentType "application/json" | Out-Null
1..15 | ForEach-Object {
    Invoke-RestMethod -Uri "$BaseUrl/api/transacoes" -Method Post -Headers $hA -Body (@{
        descricao = "Volume $_ $suffix"; valor = $_; tipoTransacao = "DESPESA"; statusConferencia = "CONFIRMADA"
        categoriaId = $catA.id; contaBancariaId = $contaA.id; dataTransacao = $dt
    } | ConvertTo-Json) -ContentType "application/json" | Out-Null
}

$baseData = 18  # header + 18 linhas de dados A
$cases = @(
    @{ n = "sem_filtro"; qs = "?dataInicio=$ini&dataFim=$fim"; min = 18; needle = $txConta }
    @{ n = "periodo"; qs = "?dataInicio=$ini&dataFim=$fim"; min = 18; needle = $txConta }
    @{ n = "conta"; qs = "?dataInicio=$ini&dataFim=$fim&contaId=$($contaA.id)"; min = 17; needle = $txConta }
    @{ n = "cartao"; qs = "?dataInicio=$ini&dataFim=$fim&cartaoId=$($cartaoA.id)"; min = 1; needle = $txCartao }
    @{ n = "categoria"; qs = "?dataInicio=$ini&dataFim=$fim&categoriaId=$($catA.id)"; min = 18; needle = $txConta }
    @{ n = "tipo_despesa"; qs = "?dataInicio=$ini&dataFim=$fim&tipoTransacao=DESPESA"; min = 17; needle = $txConta }
    @{ n = "tipo_receita"; qs = "?dataInicio=$ini&dataFim=$fim&tipoTransacao=RECEITA"; min = 1; needle = "Receita A" }
    @{ n = "status"; qs = "?dataInicio=$ini&dataFim=$fim&statusConferencia=PENDENTE"; min = 1; needle = $txCartao }
    @{ n = "descricao"; qs = "?dataInicio=$ini&dataFim=$fim&descricaoContem=cartao"; min = 1; needle = $txCartao }
    @{ n = "combinado"; qs = "?dataInicio=$ini&dataFim=$fim&contaId=$($contaA.id)&tipoTransacao=DESPESA&statusConferencia=CONFIRMADA"; min = 16; needle = $txConta }
    @{ n = "periodo_vazio"; qs = "?dataInicio=2099-01-01&dataFim=2099-01-02"; min = 0; needle = $null; headerOnly = $true }
    @{ n = "id_inexistente"; qs = "?dataInicio=$ini&dataFim=$fim&contaId=999999999"; err = $true }
    @{ n = "conta_alheia"; qs = "?dataInicio=$ini&dataFim=$fim&contaId=$($contaB.id)"; err = $true }
    @{ n = "cartao_alheio"; qs = "?dataInicio=$ini&dataFim=$fim&cartaoId=$($cartaoB.id)"; err = $true }
    @{ n = "categoria_alheia"; qs = "?dataInicio=$ini&dataFim=$fim&categoriaId=$($catB.id)"; err = $true }
)

foreach ($c in $cases) {
    if ($c.err) {
        try { Get-Csv $tokA $c.qs | Out-Null; Add-Row $c.n 0 0 $false $true }
        catch { Add-Row $c.n 0 0 $true $true }
        continue
    }
    try {
        $csv = Get-Csv $tokA $c.qs
        $dataLines = [Math]::Max(0, $csv.lines.Count - 1)
        $hdrOk = ($csv.lines.Count -gt 0) -and $csv.lines[0].StartsWith("Data,")
        $body = $csv.lines -join "`n"
        $hasNeedle = if ($c.headerOnly) { $true } elseif ($c.needle) { $body -match [regex]::Escape($c.needle) } else { $true }
        $noB = $body -notmatch [regex]::Escape($txB)
        $ok = $csv.hasBom -and $hdrOk -and ($dataLines -ge $c.min) -and $hasNeedle -and $noB
        Add-Row $c.n ($c.min + 1) ($dataLines + 1) $ok $noB
    } catch { Add-Row $c.n ($c.min + 1) 0 $false $false }
}

$fail = ($results | Where-Object { $_.resultado -eq "FALHA" }).Count
if (-not $Relatorio) { $Relatorio = Join-Path $root "logs\csv-validacao-$suffix.json" }
New-Item -ItemType Directory -Force -Path (Split-Path $Relatorio -Parent) | Out-Null
@{ timestamp = (Get-Date).ToString("o"); casos = $results; total = $results.Count; aprovados = $results.Count - $fail; falhas = $fail } | ConvertTo-Json -Depth 5 | Set-Content $Relatorio -Encoding UTF8
Write-Host "CSV: $($results.Count - $fail)/$($results.Count) OK -> $Relatorio" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
if ($fail -gt 0) { exit 1 }
