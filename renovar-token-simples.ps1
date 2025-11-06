# Script simples para renovar token do Mercado Pago
Write-Host "Renovando token do Mercado Pago..." -ForegroundColor Cyan

# Dados atuais (copie do banco)
$clientId = "4223603750190943"
$clientSecret = "APP_USR_4223603750190943-091421-axwP"
$refreshToken = "TG-678a1234567890abcdef1234567890ab-123456789"

# URL para renovar
$url = "https://api.mercadopago.com/oauth/token"

# Body da requisição
$body = @{
    grant_type = "refresh_token"
    client_id = $clientId
    client_secret = $clientSecret
    refresh_token = $refreshToken
}

try {
    Write-Host "Fazendo requisição para renovar token..." -ForegroundColor Yellow
    $response = Invoke-RestMethod -Uri $url -Method Post -Body $body -ContentType "application/x-www-form-urlencoded"
    
    if ($response.access_token) {
        Write-Host "SUCESSO! Token renovado:" -ForegroundColor Green
        Write-Host "Access Token: $($response.access_token)" -ForegroundColor White
        Write-Host "Refresh Token: $($response.refresh_token)" -ForegroundColor White
        
        Write-Host "`nCole este token no banco de dados:" -ForegroundColor Cyan
        Write-Host "UPDATE autorizacoes_bancarias SET access_token = '$($response.access_token)' WHERE tipo_banco = 'MERCADO_PAGO';" -ForegroundColor Yellow
        
    } else {
        Write-Host "ERRO: $($response)" -ForegroundColor Red
    }
    
} catch {
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
}
