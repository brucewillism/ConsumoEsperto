# Script para gerar novo token do Mercado Pago
Write-Host "Gerando novo token do Mercado Pago..." -ForegroundColor Cyan

# Dados da aplicação
$clientId = "4223603750190943"
$clientSecret = "APP_USR_4223603750190943-091421-axwP"

# URL para gerar token
$url = "https://api.mercadopago.com/oauth/token"

# Body da requisição
$body = @{
    grant_type = "client_credentials"
    client_id = $clientId
    client_secret = $clientSecret
}

try {
    Write-Host "Fazendo requisição para gerar novo token..." -ForegroundColor Yellow
    $response = Invoke-RestMethod -Uri $url -Method Post -Body $body -ContentType "application/x-www-form-urlencoded"
    
    if ($response.access_token) {
        Write-Host "SUCESSO! Novo token gerado:" -ForegroundColor Green
        Write-Host "Access Token: $($response.access_token)" -ForegroundColor White
        Write-Host "Token Type: $($response.token_type)" -ForegroundColor White
        Write-Host "Expires In: $($response.expires_in) segundos" -ForegroundColor White
        
        Write-Host "`nCole este token no banco de dados:" -ForegroundColor Cyan
        Write-Host "UPDATE autorizacoes_bancarias SET access_token = '$($response.access_token)', data_expiracao = NOW() + INTERVAL '6 hours' WHERE tipo_banco = 'MERCADO_PAGO';" -ForegroundColor Yellow
        
    } else {
        Write-Host "ERRO: $($response)" -ForegroundColor Red
    }
    
} catch {
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Resposta: $($_.Exception.Response)" -ForegroundColor Red
}
