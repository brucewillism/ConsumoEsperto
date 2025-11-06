# Script para configurar credenciais reais do Mercado Pago
# Execute este script após obter suas credenciais do painel do Mercado Pago

Write-Host "🔐 Configurando Credenciais Reais do Mercado Pago" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
Write-Host ""

# Solicitar credenciais do usuário
Write-Host "📋 Digite suas credenciais do Mercado Pago:" -ForegroundColor Yellow
Write-Host ""

$clientId = Read-Host "Client ID (ex: 1234567890123456789)"
$clientSecret = Read-Host "Client Secret (ex: abc123def456ghi789jkl012mno345pqr678stu901vwx234yz)"
$userId = Read-Host "User ID (ex: 123456789)"

Write-Host ""
Write-Host "🔧 Configurando variáveis de ambiente..." -ForegroundColor Blue

# Configurar variáveis de ambiente
$env:MERCADOPAGO_CLIENT_ID = $clientId
$env:MERCADOPAGO_CLIENT_SECRET = $clientSecret
$env:MERCADOPAGO_USER_ID = $userId

Write-Host "✅ Variáveis de ambiente configuradas!" -ForegroundColor Green
Write-Host ""

# Atualizar application.properties
Write-Host "📝 Atualizando application.properties..." -ForegroundColor Blue

$propertiesFile = "src\main\resources\application.properties"
$content = Get-Content $propertiesFile -Raw

# Substituir as credenciais
$content = $content -replace "MERCADOPAGO_CLIENT_ID=.*", "MERCADOPAGO_CLIENT_ID=$clientId"
$content = $content -replace "MERCADOPAGO_CLIENT_SECRET=.*", "MERCADOPAGO_CLIENT_SECRET=$clientSecret"
$content = $content -replace "MERCADOPAGO_USER_ID=.*", "MERCADOPAGO_USER_ID=$userId"

Set-Content -Path $propertiesFile -Value $content -Encoding UTF8

Write-Host "✅ application.properties atualizado!" -ForegroundColor Green
Write-Host ""

# Compilar o projeto
Write-Host "🔨 Compilando o projeto..." -ForegroundColor Blue
mvn clean compile -q

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Compilação bem-sucedida!" -ForegroundColor Green
} else {
    Write-Host "❌ Erro na compilação!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🎉 Configuração concluída com sucesso!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Próximos passos:" -ForegroundColor Yellow
Write-Host "1. Execute: mvn spring-boot:run" -ForegroundColor White
Write-Host "2. Acesse a aplicação no navegador" -ForegroundColor White
Write-Host "3. Vá em 'Conectar Banco' > 'Mercado Pago'" -ForegroundColor White
Write-Host "4. Faça login com sua conta do Mercado Pago" -ForegroundColor White
Write-Host "5. Verifique se os dados reais aparecem" -ForegroundColor White
Write-Host ""
Write-Host "⚠️  Importante: Mantenha suas credenciais seguras!" -ForegroundColor Red
Write-Host "   Nunca compartilhe seu Client Secret com ninguém!" -ForegroundColor Red