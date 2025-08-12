# Script para configurar ngrok e URLs das APIs bancárias
# Execute este script após iniciar o ngrok

param(
    [string]$NgrokUrl = ""
)

Write-Host "=== CONFIGURAÇÃO DO NGROK E APIS BANCÁRIAS ===" -ForegroundColor Cyan

# Se não foi fornecida uma URL, tentar obter do ngrok
if ([string]::IsNullOrEmpty($NgrokUrl)) {
    Write-Host "Tentando obter URL do ngrok..." -ForegroundColor Yellow
    
    try {
        # Tentar obter a URL do ngrok via API local
        $ngrokInfo = Invoke-RestMethod -Uri "http://localhost:4040/api/tunnels" -ErrorAction SilentlyContinue
        if ($ngrokInfo.tunnels -and $ngrokInfo.tunnels.Count -gt 0) {
            $NgrokUrl = $ngrokInfo.tunnels[0].public_url
            Write-Host "URL do ngrok obtida: $NgrokUrl" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "Não foi possível obter a URL do ngrok automaticamente." -ForegroundColor Yellow
        Write-Host "Por favor, forneça a URL manualmente ou inicie o ngrok primeiro." -ForegroundColor Red
        exit 1
    }
}

if ([string]::IsNullOrEmpty($NgrokUrl)) {
    Write-Host "Por favor, forneça a URL do ngrok como parâmetro:" -ForegroundColor Red
    Write-Host ".\setup-ngrok.ps1 -NgrokUrl 'https://seu-tunnel.ngrok.io'" -ForegroundColor Yellow
    exit 1
}

Write-Host "Configurando URLs para: $NgrokUrl" -ForegroundColor Green

# Atualizar o arquivo de configuração das APIs bancárias
$configFile = "..\backend\src\main\resources\bank-apis-config.properties"
$configContent = Get-Content $configFile -Raw

# Substituir todas as ocorrências de localhost:8080 pela URL do ngrok
$configContent = $configContent -replace 'http://localhost:8080', $NgrokUrl

# Salvar o arquivo atualizado
Set-Content -Path $configFile -Value $configContent -Encoding UTF8

Write-Host "Arquivo de configuração atualizado com sucesso!" -ForegroundColor Green

# Criar arquivo .env com as variáveis de ambiente
$envFile = "..\backend\.env"
$envContent = @"
# ========================================
# CONFIGURAÇÃO DAS APIS BANCÁRIAS
# ========================================

# ITAÚ - Open Banking
ITAU_CLIENT_ID=your_itau_client_id_here
ITAU_CLIENT_SECRET=your_itau_client_secret_here

# MERCADO PAGO
MERCADOPAGO_CLIENT_ID=your_mercadopago_client_id_here
MERCADOPAGO_CLIENT_SECRET=your_mercadopago_client_secret_here

# INTER - Open Banking
INTER_CLIENT_ID=your_inter_client_id_here
INTER_CLIENT_SECRET=your_inter_client_secret_here

# NUBANK (quando disponível)
NUBANK_CLIENT_ID=your_nubank_client_id_here
NUBANK_CLIENT_SECRET=your_nubank_client_secret_here

# URL do ngrok
NGROK_URL=$NgrokUrl

# ========================================
# CONFIGURAÇÕES ADICIONAIS
# ========================================
BANK_API_TIMEOUT=30000
BANK_API_MAX_RETRIES=3
BANK_API_RETRY_DELAY=1000
"@

Set-Content -Path $envFile -Value $envContent -Encoding UTF8

Write-Host "Arquivo .env criado com sucesso!" -ForegroundColor Green

Write-Host "`n=== PRÓXIMOS PASSOS ===" -ForegroundColor Cyan
Write-Host "1. Configure suas credenciais no arquivo .env" -ForegroundColor Yellow
Write-Host "2. Reinicie o backend para aplicar as configurações" -ForegroundColor Yellow
Write-Host "3. Teste as APIs bancárias" -ForegroundColor Yellow

Write-Host "`n=== URLs CONFIGURADAS ===" -ForegroundColor Cyan
Write-Host "Backend: $NgrokUrl" -ForegroundColor Green
Write-Host "Swagger: $NgrokUrl/swagger-ui.html" -ForegroundColor Green
Write-Host "Health Check: $NgrokUrl/actuator/health" -ForegroundColor Green
