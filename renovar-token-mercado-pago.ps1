# Script para renovar token do Mercado Pago
Write-Host "🔄 RENOVANDO TOKEN DO MERCADO PAGO" -ForegroundColor Cyan
Write-Host "=" * 50

# Configurações do banco
$connectionString = "jdbc:postgresql://localhost:5432/consumo_esperto"
$username = "postgres"
$password = "postgres"

# Buscar configuração atual
Write-Host "🔍 Buscando configuração atual..." -ForegroundColor Yellow
$configQuery = @"
SELECT client_id, client_secret, api_url 
FROM bank_api_configs 
WHERE tipo_banco = 'MERCADO_PAGO' AND ativo = true
"@

# Buscar autorização atual
Write-Host "🔍 Buscando autorização atual..." -ForegroundColor Yellow
$authQuery = @"
SELECT access_token, refresh_token, data_expiracao
FROM autorizacoes_bancarias 
WHERE tipo_banco = 'MERCADO_PAGO' AND ativo = true
"@

# Executar queries
try {
    $config = Invoke-Sqlcmd -ConnectionString $connectionString -Query $configQuery -Username $username -Password $password
    $auth = Invoke-Sqlcmd -ConnectionString $connectionString -Query $authQuery -Username $username -Password $password
    
    if ($config -and $auth) {
        Write-Host "✅ Configuração encontrada:" -ForegroundColor Green
        Write-Host "   - Client ID: $($config.client_id)" -ForegroundColor White
        Write-Host "   - Client Secret: $($config.client_secret.Substring(0,10))..." -ForegroundColor White
        Write-Host "   - Access Token: $($auth.access_token.Substring(0,20))..." -ForegroundColor White
        Write-Host "   - Data Expiração: $($auth.data_expiracao)" -ForegroundColor White
        
        # Renovar token
        Write-Host "`n🔄 Renovando token..." -ForegroundColor Yellow
        
        $refreshUrl = "https://api.mercadopago.com/oauth/token"
        $body = @{
            grant_type = "refresh_token"
            client_id = $config.client_id
            client_secret = $config.client_secret
            refresh_token = $auth.refresh_token
        }
        
        $response = Invoke-RestMethod -Uri $refreshUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded"
        
        if ($response.access_token) {
            Write-Host "✅ Token renovado com sucesso!" -ForegroundColor Green
            Write-Host "   - Novo Access Token: $($response.access_token.Substring(0,20))..." -ForegroundColor White
            
            # Atualizar no banco
            $updateQuery = @"
UPDATE autorizacoes_bancarias 
SET access_token = '$($response.access_token)',
    refresh_token = '$($response.refresh_token)',
    data_expiracao = NOW() + INTERVAL '6 hours',
    data_atualizacao = NOW()
WHERE tipo_banco = 'MERCADO_PAGO' AND ativo = true
"@
            
            Invoke-Sqlcmd -ConnectionString $connectionString -Query $updateQuery -Username $username -Password $password
            
            Write-Host "✅ Token atualizado no banco de dados!" -ForegroundColor Green
            Write-Host "`n🎉 PRONTO! Agora teste novamente no frontend." -ForegroundColor Cyan
            
        } else {
            Write-Host "❌ Erro ao renovar token: $($response)" -ForegroundColor Red
        }
        
    } else {
        Write-Host "❌ Configuração ou autorização não encontrada!" -ForegroundColor Red
    }
    
} catch {
    Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n" + "=" * 50
Write-Host "Script finalizado!" -ForegroundColor Cyan
