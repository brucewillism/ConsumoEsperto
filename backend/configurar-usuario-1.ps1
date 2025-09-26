# Script para configurar credenciais do Mercado Pago para usuário 1
# Este script configura automaticamente as credenciais do Mercado Pago

Write-Host "🔧 Configurando credenciais do Mercado Pago para usuário 1..." -ForegroundColor Yellow

# URL da API
$apiUrl = "http://localhost:8080/api/mercadopago/auto-config/configure"

# Headers para autenticação (você precisa estar logado)
$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer SEU_JWT_TOKEN_AQUI"
}

try {
    Write-Host "📡 Fazendo requisição para configurar credenciais..." -ForegroundColor Blue
    
    # Fazer requisição POST
    $response = Invoke-RestMethod -Uri $apiUrl -Method POST -Headers $headers -Body "{}"
    
    Write-Host "✅ Configuração realizada com sucesso!" -ForegroundColor Green
    Write-Host "📊 Resposta: $($response | ConvertTo-Json -Depth 3)" -ForegroundColor Cyan
    
} catch {
    Write-Host "❌ Erro ao configurar credenciais:" -ForegroundColor Red
    Write-Host "   Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "   Mensagem: $($_.Exception.Message)" -ForegroundColor Red
    
    Write-Host "`n💡 Dica: Você precisa estar logado no sistema primeiro!" -ForegroundColor Yellow
    Write-Host "   1. Acesse: https://85766d45517b.ngrok-free.app" -ForegroundColor Yellow
    Write-Host "   2. Faça login com Google" -ForegroundColor Yellow
    Write-Host "   3. Execute este script novamente" -ForegroundColor Yellow
}

Write-Host "`n🏁 Script finalizado!" -ForegroundColor Green
