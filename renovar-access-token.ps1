Write-Host "Renovando Access Token do Mercado Pago..." -ForegroundColor Green

# Credenciais do Mercado Pago
$CLIENT_ID = "4223603750190943"
$CLIENT_SECRET = "D3pZ1tvPtRXlo8m6QGXVmekh9jZsaxwP"
$NGROK_URL = "https://262f3e49bd2d.ngrok-free.app"

Write-Host "Client ID: $CLIENT_ID" -ForegroundColor Yellow
Write-Host "Ngrok URL: $NGROK_URL" -ForegroundColor Yellow

# URL para renovar o access token
$tokenUrl = "https://api.mercadopago.com/oauth/token"

# Dados para renovar o token
$body = @{
    grant_type = "client_credentials"
    client_id = $CLIENT_ID
    client_secret = $CLIENT_SECRET
} | ConvertTo-Json

Write-Host "Fazendo requisição para renovar o token..." -ForegroundColor Yellow

try {
    $response = Invoke-RestMethod -Uri $tokenUrl -Method POST -Body $body -ContentType "application/json"
    
    if ($response.access_token) {
        Write-Host "✅ Access Token renovado com sucesso!" -ForegroundColor Green
        Write-Host "Novo Access Token: $($response.access_token)" -ForegroundColor Cyan
        
        # Calcular data de expiração (6 horas a partir de agora)
        $dataExpiracao = (Get-Date).AddHours(6).ToString("yyyy-MM-dd HH:mm:ss")
        
        # Atualizar no banco de dados
        $env:PGPASSWORD = "0301HeX@"
        $updateQuery = "UPDATE autorizacoes_bancarias SET access_token = '$($response.access_token)', data_expiracao = '$dataExpiracao', data_atualizacao = NOW() WHERE tipo_banco = 'MERCADO_PAGO';"
        
        $result = psql -h localhost -U bruce -d consumoesperto -c $updateQuery
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Access Token atualizado no banco de dados!" -ForegroundColor Green
        } else {
            Write-Host "❌ Erro ao atualizar Access Token no banco de dados" -ForegroundColor Red
        }
        
    } else {
        Write-Host "❌ Erro ao renovar Access Token" -ForegroundColor Red
        Write-Host "Resposta: $($response | ConvertTo-Json)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Erro na requisição: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`nRenovação do Access Token concluída!" -ForegroundColor Green
