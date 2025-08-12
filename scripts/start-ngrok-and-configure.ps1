# Script completo para iniciar ngrok e configurar APIs bancárias
# Este script inicia o ngrok e configura automaticamente as URLs

Write-Host "=== INICIANDO NGROK E CONFIGURANDO APIS BANCÁRIAS ===" -ForegroundColor Cyan

# Verificar se o ngrok está instalado
$ngrokPath = Get-Command ngrok -ErrorAction SilentlyContinue
if (-not $ngrokPath) {
    Write-Host "ngrok não encontrado. Por favor, instale o ngrok primeiro." -ForegroundColor Red
    Write-Host "Download: https://ngrok.com/download" -ForegroundColor Yellow
    Write-Host "Após instalar, adicione o ngrok ao PATH ou coloque o executável nesta pasta." -ForegroundColor Yellow
    exit 1
}

# Verificar se o backend está rodando
$port8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if (-not $port8080) {
    Write-Host "Backend não está rodando na porta 8080. Iniciando o backend..." -ForegroundColor Yellow
    
    # Navegar para o diretório do backend
    Set-Location "..\backend"
    
    # Tentar iniciar o backend em background
    Start-Process -FilePath "powershell" -ArgumentList "-Command", "mvn spring-boot:run" -WindowStyle Minimized
    
    # Aguardar o backend iniciar
    Write-Host "Aguardando o backend iniciar..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
    
    # Voltar para o diretório scripts
    Set-Location "..\scripts"
}

# Iniciar ngrok em background
Write-Host "Iniciando ngrok na porta 8080..." -ForegroundColor Yellow
Start-Process -FilePath "ngrok" -ArgumentList "http", "8080" -WindowStyle Minimized

# Aguardar o ngrok iniciar
Write-Host "Aguardando o ngrok iniciar..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Tentar obter a URL do ngrok
$maxAttempts = 10
$attempt = 0
$ngrokUrl = ""

while ($attempt -lt $maxAttempts -and [string]::IsNullOrEmpty($ngrokUrl)) {
    $attempt++
    Write-Host "Tentativa $attempt de obter URL do ngrok..." -ForegroundColor Yellow
    
    try {
        $ngrokInfo = Invoke-RestMethod -Uri "http://localhost:4040/api/tunnels" -ErrorAction SilentlyContinue
        if ($ngrokInfo.tunnels -and $ngrokInfo.tunnels.Count -gt 0) {
            $ngrokUrl = $ngrokInfo.tunnels[0].public_url
            Write-Host "URL do ngrok obtida: $ngrokUrl" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "Tentativa $attempt falhou. Aguardando..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
}

if ([string]::IsNullOrEmpty($ngrokUrl)) {
    Write-Host "Não foi possível obter a URL do ngrok automaticamente." -ForegroundColor Red
    Write-Host "Por favor, execute manualmente: .\setup-ngrok.ps1 -NgrokUrl 'sua-url-aqui'" -ForegroundColor Yellow
    exit 1
}

# Configurar as APIs bancárias com a URL do ngrok
Write-Host "Configurando APIs bancárias..." -ForegroundColor Yellow
& ".\setup-ngrok.ps1" -NgrokUrl $ngrokUrl

Write-Host "`n=== CONFIGURAÇÃO COMPLETA! ===" -ForegroundColor Green
Write-Host "ngrok está rodando em: $ngrokUrl" -ForegroundColor Cyan
Write-Host "Backend está configurado para usar esta URL" -ForegroundColor Cyan
Write-Host "APIs bancárias estão configuradas" -ForegroundColor Cyan

Write-Host "`n=== COMO USAR ===" -ForegroundColor Yellow
Write-Host "1. Acesse o Swagger: $ngrokUrl/swagger-ui.html" -ForegroundColor White
Write-Host "2. Configure suas credenciais no arquivo .env" -ForegroundColor White
Write-Host "3. Teste as APIs bancárias" -ForegroundColor White

Write-Host "`n=== PARA PARAR ===" -ForegroundColor Yellow
Write-Host "Para parar o ngrok, feche a janela ou use Ctrl+C" -ForegroundColor White
Write-Host "Para parar o backend, feche a janela do PowerShell" -ForegroundColor White
